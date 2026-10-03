"""Builds VRClip's depth models from the official Depth Anything V2 Small weights.

The exported graphs run entirely on mobile GPUs. The reference implementation uses operations that
the LiteRT GPU delegate rejects on many devices (transposed convolutions, 5D and 6D tensors in
attention and in the patch reshapes), which pushes parts of every inference back to the CPU.
This script rebuilds the same network with equivalent operations that keep every tensor within
four dimensions and avoid transposed convolutions:

- attention uses separate query, key and value projections and 4D tensors only;
- LayerScale factors and the attention scale are folded into the neighbouring weights;
- the position embedding is interpolated once for the export size and stored as a constant;
- each transposed convolution with kernel == stride becomes a 1x1 convolution followed by a
  pixel shuffle written with 4D reshapes and transposes.

All of these are exact rewrites: the export is checked against the reference model.

Weights are stored in float16. Input: [1, H, W, 3] float32 RGB in 0..1. Output: [1, H, W] float32
relative inverse depth (larger is nearer), as in the reference model.

The models to build (names, input sizes and the SHA-256 the app pins) are listed in models.json.

Usage:
    python export.py --repo Depth-Anything-V2 --weights depth_anything_v2_vits.pth --out out/ \
        [--verify-pins] [--images a.jpg b.jpg] [--selftest-dir app/src/main/assets/depth]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

MANIFEST = Path(__file__).with_name("models.json")

MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)

# Operations the LiteRT GPU delegate does not run on common mobile GPUs.
GPU_UNFRIENDLY_OPS = {"TRANSPOSE_CONV", "GATHER", "GATHER_ND", "BROADCAST_TO", "SELECT_V2"}


def load_reference(repo: Path, weights: Path) -> nn.Module:
    sys.path.insert(0, str(repo))
    from depth_anything_v2.dpt import DepthAnythingV2  # noqa: E402

    model = DepthAnythingV2(encoder="vits", features=64, out_channels=[48, 96, 192, 384])
    model.load_state_dict(torch.load(weights, map_location="cpu", weights_only=True))
    return model.eval()


class Attention(nn.Module):
    """Multi-head self attention with separate projections and 4D tensors only."""

    def __init__(self, reference: nn.Module):
        super().__init__()
        dim = reference.qkv.in_features
        self.heads = reference.num_heads
        self.head_dim = dim // self.heads
        weight = reference.qkv.weight.detach()
        bias = reference.qkv.bias.detach()
        self.q = nn.Linear(dim, dim)
        self.k = nn.Linear(dim, dim)
        self.v = nn.Linear(dim, dim)
        scale = reference.scale
        with torch.no_grad():
            self.q.weight.copy_(weight[:dim] * scale)
            self.q.bias.copy_(bias[:dim] * scale)
            self.k.weight.copy_(weight[dim : 2 * dim])
            self.k.bias.copy_(bias[dim : 2 * dim])
            self.v.weight.copy_(weight[2 * dim :])
            self.v.bias.copy_(bias[2 * dim :])
        self.proj = nn.Linear(dim, dim)
        self.proj.load_state_dict(reference.proj.state_dict())

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        n = x.shape[1]
        q = self.q(x).reshape(1, n, self.heads, self.head_dim).permute(0, 2, 1, 3)
        k = self.k(x).reshape(1, n, self.heads, self.head_dim).permute(0, 2, 3, 1)
        v = self.v(x).reshape(1, n, self.heads, self.head_dim).permute(0, 2, 1, 3)
        attention = torch.softmax(torch.matmul(q, k), dim=-1)
        out = torch.matmul(attention, v).permute(0, 2, 1, 3).reshape(1, n, self.heads * self.head_dim)
        return self.proj(out)


def fold_layer_scale(linear: nn.Linear, layer_scale: nn.Module) -> nn.Linear:
    """Returns a copy of [linear] whose output is already multiplied by the LayerScale gamma."""
    folded = nn.Linear(linear.in_features, linear.out_features)
    gamma = layer_scale.gamma.detach() if hasattr(layer_scale, "gamma") else torch.ones(linear.out_features)
    with torch.no_grad():
        folded.weight.copy_(linear.weight * gamma[:, None])
        folded.bias.copy_(linear.bias * gamma)
    return folded


class Block(nn.Module):
    def __init__(self, reference: nn.Module):
        super().__init__()
        self.norm1 = reference.norm1
        self.attn = Attention(reference.attn)
        self.attn.proj = fold_layer_scale(self.attn.proj, reference.ls1)
        self.norm2 = reference.norm2
        self.fc1 = reference.mlp.fc1
        self.act = nn.GELU()
        self.fc2 = fold_layer_scale(reference.mlp.fc2, reference.ls2)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.attn(self.norm1(x))
        return x + self.fc2(self.act(self.fc1(self.norm2(x))))


class PixelShuffleUpsample(nn.Module):
    """Exact replacement for ConvTranspose2d(c, c, kernel_size=s, stride=s, padding=0)."""

    def __init__(self, reference: nn.ConvTranspose2d):
        super().__init__()
        s = reference.stride[0]
        assert reference.kernel_size == (s, s) and reference.padding == (0, 0)
        c_in, c_out = reference.in_channels, reference.out_channels
        self.scale = s
        self.conv = nn.Conv2d(c_in, c_out * s * s, kernel_size=1)
        # ConvTranspose2d weight is [c_in, c_out, s, s]; pixel_shuffle reads channel c * s * s + i * s + j.
        weight = reference.weight.detach().permute(1, 2, 3, 0).reshape(c_out * s * s, c_in, 1, 1)
        with torch.no_grad():
            self.conv.weight.copy_(weight)
            self.conv.bias.copy_(reference.bias.detach().repeat_interleave(s * s))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # F.pixel_shuffle lowers through a 6D reshape; the same permutation in two 4D steps:
        # channel (c, i, j) at (h, w) goes to (c, h * s + i, w * s + j).
        s = self.scale
        z = self.conv(x)
        _, cs2, h, w = z.shape
        c = cs2 // (s * s)
        z = z.reshape(c * s, s, h, w).permute(0, 2, 3, 1).reshape(1, c * s, h, w * s)
        z = z.reshape(c, s, h, w * s).permute(0, 2, 1, 3).reshape(1, c, h * s, w * s)
        return z


class FusionBlock(nn.Module):
    def __init__(self, reference: nn.Module):
        super().__init__()
        self.res1 = reference.resConfUnit1
        self.res2 = reference.resConfUnit2
        self.out_conv = reference.out_conv

    @staticmethod
    def residual(unit: nn.Module, x: torch.Tensor) -> torch.Tensor:
        out = unit.conv1(F.relu(x))
        out = unit.conv2(F.relu(out))
        return out + x

    def forward(self, x: torch.Tensor, skip: torch.Tensor | None, size: tuple[int, int]) -> torch.Tensor:
        if skip is not None:
            x = x + self.residual(self.res1, skip)
        x = self.residual(self.res2, x)
        x = F.interpolate(x, size=size, mode="bilinear", align_corners=True)
        return self.out_conv(x)


class GpuDepthAnything(nn.Module):
    def __init__(self, reference: nn.Module, size: int):
        super().__init__()
        assert size % 14 == 0
        self.size = size
        self.patches = size // 14
        encoder = reference.pretrained
        self.patch_embed = encoder.patch_embed.proj
        self.cls_token = nn.Parameter(encoder.cls_token.detach().clone(), requires_grad=False)
        tokens = torch.zeros(1, 1 + self.patches * self.patches, encoder.embed_dim)
        with torch.no_grad():
            pos = encoder.interpolate_pos_encoding(tokens, size, size)
        self.register_buffer("pos_embed", pos.detach().clone())
        self.blocks = nn.ModuleList(Block(b) for b in encoder.blocks)
        self.take = reference.intermediate_layer_idx[reference.encoder]
        self.norm = encoder.norm

        head = reference.depth_head
        self.projects = head.projects
        self.resize = nn.ModuleList(
            [
                PixelShuffleUpsample(head.resize_layers[0]),
                PixelShuffleUpsample(head.resize_layers[1]),
                nn.Identity(),
                head.resize_layers[3],
            ]
        )
        scratch = head.scratch
        self.layer_rn = nn.ModuleList([scratch.layer1_rn, scratch.layer2_rn, scratch.layer3_rn, scratch.layer4_rn])
        self.refine = nn.ModuleList(
            [FusionBlock(scratch.refinenet1), FusionBlock(scratch.refinenet2), FusionBlock(scratch.refinenet3), FusionBlock(scratch.refinenet4)]
        )
        self.output_conv1 = scratch.output_conv1
        self.output_conv2 = scratch.output_conv2
        self.register_buffer("mean", torch.tensor(MEAN).reshape(1, 1, 1, 3))
        self.register_buffer("inv_std", 1.0 / torch.tensor(STD).reshape(1, 1, 1, 3))

    def forward(self, rgb: torch.Tensor) -> torch.Tensor:
        # [1, H, W, 3] in 0..1 to the normalized NCHW tensor the network was trained on.
        x = ((rgb - self.mean) * self.inv_std).permute(0, 3, 1, 2)
        p = self.patches
        x = self.patch_embed(x).flatten(2).transpose(1, 2)
        x = torch.cat((self.cls_token, x), dim=1) + self.pos_embed
        features = []
        for i, block in enumerate(self.blocks):
            x = block(x)
            if i in self.take:
                features.append(self.norm(x)[:, 1:])

        layers = []
        for i, tokens in enumerate(features):
            f = tokens.transpose(1, 2).reshape(1, tokens.shape[-1], p, p)
            f = self.resize[i](self.projects[i](f))
            layers.append(self.layer_rn[i](f))
        l1, l2, l3, l4 = layers
        path = self.refine[3](l4, None, tuple(l3.shape[2:]))
        path = self.refine[2](path, l3, tuple(l2.shape[2:]))
        path = self.refine[1](path, l2, tuple(l1.shape[2:]))
        path = self.refine[0](path, l1, (l1.shape[2] * 2, l1.shape[3] * 2))
        out = self.output_conv1(path)
        out = F.interpolate(out, size=(self.size, self.size), mode="bilinear", align_corners=True)
        out = self.output_conv2(out)
        return F.relu(out)[:, 0]


def reference_depth(reference: nn.Module, rgb: torch.Tensor) -> torch.Tensor:
    x = ((rgb - torch.tensor(MEAN).reshape(1, 1, 1, 3)) / torch.tensor(STD).reshape(1, 1, 1, 3)).permute(0, 3, 1, 2)
    return reference(x)


def correlation(a: np.ndarray, b: np.ndarray) -> float:
    a = a.ravel().astype(np.float64)
    b = b.ravel().astype(np.float64)
    a -= a.mean()
    b -= b.mean()
    return float((a * b).sum() / math.sqrt((a * a).sum() * (b * b).sum()))


def load_images(paths: list[Path], size: int) -> list[torch.Tensor]:
    from PIL import Image

    images = []
    for path in paths:
        img = Image.open(path).convert("RGB").resize((size, size), Image.BICUBIC)
        images.append(torch.from_numpy(np.asarray(img, dtype=np.float32) / 255.0)[None])
    return images


def op_census(model_path: Path) -> tuple[dict[str, int], int]:
    from ai_edge_litert import interpreter as tfl

    interpreter = tfl.Interpreter(model_path=str(model_path))
    ops: dict[str, int] = {}
    for op in interpreter._get_ops_details():  # noqa: SLF001 - no public API for this
        ops[op["op_name"]] = ops.get(op["op_name"], 0) + 1
    max_rank = max(len(t["shape"]) for t in interpreter.get_tensor_details())
    return ops, max_rank


def run_tflite(model_path: Path, rgb: torch.Tensor) -> np.ndarray:
    from ai_edge_litert import interpreter as tfl

    interpreter = tfl.Interpreter(model_path=str(model_path), num_threads=4)
    interpreter.allocate_tensors()
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], rgb.numpy())
    interpreter.invoke()
    return interpreter.get_tensor(interpreter.get_output_details()[0]["index"])


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_selftest(model_path: Path, size: int, out_dir: Path, name: str) -> None:
    """Expected output of [model_path] for selftest_<size>.webp, averaged over the 14 px patches.

    The app runs the same image through the model when it loads it on the GPU and compares the
    result with this grid, which catches drivers whose reduced precision corrupts the depth map.
    The image is stored at the model's input size so the app decodes exactly these pixels.
    """
    from PIL import Image

    rgb = Image.open(out_dir / f"selftest_{size}.webp").convert("RGB")
    assert rgb.size == (size, size)
    x = torch.from_numpy(np.asarray(rgb, dtype=np.float32) / 255.0)[None]
    depth = run_tflite(model_path, x).reshape(size, size)
    g = size // 14
    grid = depth.reshape(g, 14, g, 14).mean(axis=(1, 3)).astype("<f4")
    (out_dir / f"selftest_{name}.bin").write_bytes(grid.tobytes())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", type=Path, required=True, help="checkout of DepthAnything/Depth-Anything-V2")
    parser.add_argument("--weights", type=Path, required=True, help="depth_anything_v2_vits.pth")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--verify-pins", action="store_true", help="fail unless the files match models.json")
    parser.add_argument("--images", type=Path, nargs="*", default=[], help="images for the parity check")
    parser.add_argument("--min-correlation", type=float, default=0.9999)
    parser.add_argument("--selftest-dir", type=Path, help="folder with selftest_<size>.webp; writes the grids")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())

    torch.manual_seed(0)
    import litert_torch
    from litert_torch.generative.quantize import quant_recipes

    reference = load_reference(args.repo, args.weights)
    args.out.mkdir(parents=True, exist_ok=True)
    report: dict[str, dict] = {}
    ok = True
    for spec in manifest["models"]:
        name, size = spec["name"], spec["input_size"]
        model = GpuDepthAnything(reference, size).eval()
        images = load_images(args.images, size) or [torch.rand(1, size, size, 3)]
        with torch.no_grad():
            torch_corr = [correlation(model(x).numpy(), reference_depth(reference, x).numpy()) for x in images]

        sample = (torch.rand(1, size, size, 3),)
        edge = litert_torch.convert(model, sample, quant_config=quant_recipes.full_fp16_recipe())
        path = args.out / spec["file"]
        edge.export(str(path))

        ops, max_rank = op_census(path)
        unfriendly = sorted(set(ops) & GPU_UNFRIENDLY_OPS)
        with torch.no_grad():
            tflite_corr = [correlation(run_tflite(path, x), reference_depth(reference, x).numpy()) for x in images]
        entry = {
            "file": path.name,
            "size": size,
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "max_tensor_rank": max_rank,
            "gpu_unfriendly_ops": unfriendly,
            "ops": dict(sorted(ops.items())),
            "correlation_torch_rewrite_vs_reference": torch_corr,
            "correlation_tflite_vs_reference": tflite_corr,
        }
        report[name] = entry
        print(json.dumps({k: v for k, v in entry.items() if k != "ops"}, indent=2))
        if unfriendly or max_rank > 4 or min(tflite_corr) < args.min_correlation:
            print(f"{name}: failed the GPU or parity checks", file=sys.stderr)
            ok = False
        if args.verify_pins and (entry["sha256"] != spec["sha256"] or entry["bytes"] != spec["bytes"]):
            print(f"{name}: built {entry['sha256']} ({entry['bytes']} bytes), models.json pins {spec['sha256']}", file=sys.stderr)
            ok = False
        if args.selftest_dir:
            write_selftest(path, size, args.selftest_dir, name)
    (args.out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
