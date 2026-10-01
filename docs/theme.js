// Light, dark or system theme, shared by every page. The inline script in each page's head applies a
// saved choice before the first paint; this file wires the controls and remembers changes.
(() => {
  "use strict";

  const KEY = "vrclip-theme";
  const COLORS = { dark: "#05070d", light: "#f5f6fb" };
  const root = document.documentElement;
  const systemLight = window.matchMedia("(prefers-color-scheme: light)");
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  function read() {
    try {
      const value = window.localStorage.getItem(KEY);
      return value === "light" || value === "dark" ? value : "system";
    } catch (e) {
      return "system";
    }
  }

  function write(value) {
    try {
      if (value === "system") window.localStorage.removeItem(KEY);
      else window.localStorage.setItem(KEY, value);
    } catch (e) {
      /* private mode: the choice lasts until the page is closed */
    }
  }

  let choice = read();
  const resolve = (value) => (value === "system" ? (systemLight.matches ? "light" : "dark") : value);
  const effective = () => resolve(choice);

  function apply() {
    if (choice === "system") root.removeAttribute("data-theme");
    else root.setAttribute("data-theme", choice);
    const now = effective();
    document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => {
      if (meta.dataset.media === undefined) meta.dataset.media = meta.getAttribute("media") || "";
      if (choice === "system") {
        if (meta.dataset.media) meta.setAttribute("media", meta.dataset.media);
        meta.content = /light/.test(meta.dataset.media) ? COLORS.light : COLORS.dark;
      } else {
        meta.removeAttribute("media");
        meta.content = COLORS[now];
      }
    });
    document.querySelectorAll(".theme-toggle").forEach((b) => b.setAttribute("aria-pressed", String(now === "dark")));
    document.querySelectorAll("[data-theme-choice]").forEach((b) => {
      b.setAttribute("aria-pressed", String(b.dataset.themeChoice === choice));
    });
  }

  /** Changes the theme; with a view transition the new one grows out of the control that was pressed. */
  function set(next, origin) {
    const run = () => {
      choice = next;
      write(next);
      apply();
    };
    // Without view transitions, with reduced motion, or when the colors stay the same (for example
    // "system" while the system is already dark), switch at once.
    if (!document.startViewTransition || reduceMotion.matches || !origin || resolve(next) === effective()) {
      run();
      return;
    }
    const r = origin.getBoundingClientRect();
    const x = r.left + r.width / 2;
    const y = r.top + r.height / 2;
    const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y));
    root.classList.add("theme-switching");
    const transition = document.startViewTransition(run);
    transition.ready
      .then(() => {
        root.animate(
          { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
          { duration: 560, easing: "cubic-bezier(0.2, 0, 0, 1)", pseudoElement: "::view-transition-new(root)" }
        );
      })
      .catch(() => {});
    transition.finished.finally(() => root.classList.remove("theme-switching"));
  }

  document.querySelectorAll(".theme-toggle").forEach((b) => {
    b.addEventListener("click", () => set(effective() === "dark" ? "light" : "dark", b));
  });
  document.querySelectorAll("[data-theme-choice]").forEach((b) => {
    b.addEventListener("click", () => set(b.dataset.themeChoice, b));
  });
  systemLight.addEventListener("change", () => {
    if (choice === "system") apply();
  });
  window.addEventListener("storage", (e) => {
    if (e.key === KEY) {
      choice = read();
      apply();
    }
  });

  apply();
})();
