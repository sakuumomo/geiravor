# Docs

Three kinds of writing. Do not copy one into another.

| Kind | Where | Authority |
|---|---|---|
| **Product spec** | [`spec/`](spec/) | What a listener gets. Live r-a-d.io beats examples; then the spec beats code. |
| **Repo docs** | `architecture.md`, `build.md`, `testing.md`, `compat.md` (added when those contracts are written) | How this tree is built, tested, and split. The site is irrelevant. |
| **Entry / ops** | Root `README.md`, `AGENTS.md`, `CHANGELOG.md`, script comments | Commands and don’ts. |

Generated crate API (`cargo doc` from `///`) is a **view of source comments**, not a fourth spec. AI does not treat `target/doc/` as truth.

Product spec files are named, not `000-` prefixed. Reading order is this index, once the spec exists.
