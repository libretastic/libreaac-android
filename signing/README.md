# LibreAAC release signing

`libreaac-release.keystore` is the permanent encrypted signing keystore for the
LibreAAC Android application. It is versioned in this private repository so
future releases can be signed with the same identity.

The keystore password is intentionally separate. On the release workstation it
is stored in the ignored, permission-restricted `keystore.properties` file in
this directory. See [BUILD.md](../BUILD.md) for the supported property and
environment-variable names.

Both the keystore and its password are irreplaceable release assets. Losing
either prevents publishing updates that Android will accept over an installed
LibreAAC release. Keep independent secure backups and do not disclose or commit
the password file.
