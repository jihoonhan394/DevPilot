package com.devpilot.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * port: 검증된 JWT → {@link CurrentUser} (docs/03 §2.2 규칙 3, §4.3). {@code user} 모듈의 {@code
 * UserProvisioningService}가 구현한다. allowlist에서 거부하면 {@code ForbiddenException(USER_NOT_ALLOWED)}를
 * 던진다. 문서의 {@code ResolvedUser}는 {@link CurrentUser}와 같은 값이라 따로 두지 않는다.
 */
public interface AuthenticatedUserResolver {

    CurrentUser resolve(Jwt jwt);
}
