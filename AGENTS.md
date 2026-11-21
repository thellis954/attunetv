# AttuneTV repository workflow

- Read README.md and CONTRIBUTING.md before changes.
- Start feature branches from current origin/main. Preserve uncommitted work and other contributors’ commits.
- Push changes to a feature branch and open a pull request targeting main. Do not push directly to main.
- Wait for Build APK / build and Samsung companion preview / verify on the current PR commit, resolve failures and review feedback, then merge within the user’s authorization. Do not bypass required checks or branch protection.
- Production follows main. Release tags must reference commits merged into main; see docs/releasing.md.
- Keep device verification claims precise. Never commit API keys, pairing credentials, signing material or private device dumps.
