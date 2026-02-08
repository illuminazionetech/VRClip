## 2025-05-14 - [Accessibility: Semantic Clearing Misuse]
**Learning:** `Modifier.clearAndSetSemantics {}` is frequently used in this project to clean up the UI for screen readers. While appropriate for removing redundant focus on children of a `selectable` or `toggleable` parent, it was incorrectly applied to `IconButton` components that represented separate, distinct actions (like "More Actions" or "Toggle View"). This made these critical buttons completely unreachable via TalkBack.
**Action:** Before applying `clearAndSetSemantics {}`, verify if the element is a child of a component that already handles its state and action. If the element provides a separate action, use `semantics { mergeDescendants = true }` or no semantic modifier at all to ensure it remains accessible.

## 2025-05-15 - [Accessibility: Missing Label in Expanded Navigation]
**Learning:** In large-screen (expanded) layouts where a navigation rail is used, auxiliary buttons like the "Open Drawer" (Menu) button can easily be overlooked for accessibility labels. While the standard drawer button in compact mode often has a label, the counterpart in the expanded rail must also explicitly provide a `contentDescription`.
**Action:** Always verify that every `IconButton` in all layout variations (Compact, Medium, Expanded) has a non-null `contentDescription` or a merged descendant providing one.
