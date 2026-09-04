# Release audit

The repository release workflow validates generated Java normalization, static Java sanity checks, Java 17, Gradle 8.9, release compilation, APK existence, and ZIP integrity.

Production store signing must use a keystore supplied through CI secrets and must never be committed to source control.
