# Wethaq Release Audit

Automated release audit marker. This file documents that the release pipeline must validate source, generated Java normalization, static sanity checks, Gradle compilation, APK integrity, and artifact publication.

Release signing keys must never be committed; production signing must be supplied through CI secrets before store publication.
