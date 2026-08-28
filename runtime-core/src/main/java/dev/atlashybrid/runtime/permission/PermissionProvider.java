package dev.atlashybrid.runtime.permission;

import java.util.Optional;

@FunctionalInterface
public interface PermissionProvider {
    Optional<Boolean> query(PermissionSubject subject, String permission);
}
