<!--
Summary of the change and the PLAN.md phase it belongs to.
-->

Checklist:

- [ ] `./gradlew spotlessCheck :app:testDebugUnitTest verifySqlDelightMigration` passes
- [ ] Debug build installed and smoke-tested on a device
- [ ] New strings in `i18n/.../base/strings.xml`, no hardcoded copy
- [ ] New columns have a migration
- [ ] No paywall, login or locked-chapter boundary weakened
- [ ] Mihon files touched only where unavoidable
