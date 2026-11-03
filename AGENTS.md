# Documentation navigation

- Begin with `docs/README.md` on the current branch; read `docs/release.md` for release tasks.
- Documentation describes the current branch. Do not assume the other variant's behavior; inspect actual branch code when documentation leaves a relevant detail unclear.
- Historical `docs/archive/` documents are not default reading. Consult them only for a specific historical question or missing detail.
- Current confirmed requirements supersede historical plans.

# Visual playground maintenance

- Keep Android and `ui-reference/` visually aligned, including essential interaction states.
- Keep the HTML implementation lightweight; do not reproduce production business logic, persistence or lifecycle guarantees.
- Verify the affected screen/interaction when changing the preview. Do not require comprehensive browser business-logic tests for every Android change.
- Use `python preview.py` to launch; edit `ui-reference/js/fixture.js` for fictional examples. Reload resets demonstration state.
