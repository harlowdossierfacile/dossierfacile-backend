package fr.dossierfacile.api.front.security;

import com.google.common.base.Strings;
import fr.dossierfacile.api.front.exception.TenantUserApiNotFoundException;
import fr.dossierfacile.api.front.security.interfaces.AuthenticationFacade;
import fr.dossierfacile.api.front.service.interfaces.DocumentService;
import fr.dossierfacile.api.front.service.interfaces.KeycloakService;
import fr.dossierfacile.api.front.service.interfaces.LogService;
import fr.dossierfacile.api.front.service.interfaces.TenantService;
import fr.dossierfacile.api.front.util.Obfuscator;
import fr.dossierfacile.common.entity.Tenant;
import fr.dossierfacile.common.enums.DocumentCategory;
import fr.dossierfacile.common.enums.LogType;
import fr.dossierfacile.common.enums.TenantFileStatus;
import fr.dossierfacile.common.repository.TenantCommonRepository;
import fr.dossierfacile.common.repository.TenantUserApiRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
public class AuthenticationFacadeImpl implements AuthenticationFacade {

    private final TenantCommonRepository tenantRepository;
    private final TenantUserApiRepository tenantUserApiRepository;
    private final TenantService tenantService;
    private final KeycloakService keycloakService;
    private final LogService logService;
    private final DocumentService documentService;

    private String getUserEmail() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("email");
    }

    private String getFirstName() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("given_name");
    }

    private String getLastName() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("family_name");
    }

    private String getPreferredName() {
        String preferredUsername = ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("preferred_username");
        if (Strings.isNullOrEmpty(preferredUsername) || preferredUsername.contains("@")) {
            return null;
        }
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("preferred_username");
    }

    @Override
    public String getKeycloakUserId() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("sub");
    }

    private boolean isFranceConnect() {
        var result = ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsBoolean("france-connect");
        return Optional.ofNullable(result).orElse(false);
    }

    private String getFranceConnectSub() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("france-connect-sub");
    }

    private String getFranceConnectBirthCountry() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("birthcountry");
    }

    private String getFranceConnectBirthPlace() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("birthplace");
    }

    private String getFranceConnectBirthDate() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("birthdate");
    }

    @Override
    public Tenant getTenant(Long id) {
        if (SecurityContextHolder.getContext().getAuthentication().getAuthorities().contains(new SimpleGrantedAuthority("SCOPE_api-partner"))) {
            Assert.notNull(id, "Tenant id can not be null");
            var keycloakClientId = getKeycloakClientId();
            System.out.println(keycloakClientId);
            //Tenant supposedly already linked with the client/partner
            return tenantUserApiRepository.findFirstByTenantIdAndUserApiName(id, keycloakClientId)
                    .orElseThrow(() -> new TenantUserApiNotFoundException(id, keycloakClientId))
                    .getTenant();
        } else {
            return getPrincipalAuthTenant();
        }
    }

    @Override
    public String getKeycloakClientId() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getClaimAsString("azp");
    }

    private Tenant getPrincipalAuthTenant() {
        String email = getUserEmail();
        Optional<Tenant> tenantOptional = tenantRepository.findByEmail(email);
        Tenant tenant;
        if (tenantOptional.isPresent()) {
            tenant = tenantOptional.get();
            if (!Boolean.TRUE.equals(tenant.getFranceConnect()) && isFranceConnect()) {
                log.info("Local account link to FranceConnect account, for tenant with ID {}", tenant.getId());
                logService.saveLog(LogType.FC_ACCOUNT_LINK, tenant.getId());
            }
        } else {
            if (keycloakService.isKeycloakUser(getKeycloakUserId())) {
                log.warn("Tenant " + Obfuscator.email(email) + " not exist - create it");
                tenant = new Tenant(email);
                tenant.setKeycloakId(getKeycloakUserId());
                tenant = tenantService.create(tenant);
                if (isFranceConnect()) {
                    log.info("Local account creation via FranceConnect account, for tenant with ID {}", tenant.getId());
                    logService.saveLog(LogType.FC_ACCOUNT_CREATION, tenant.getId());
                }
            } else {
                throw new AccessDeniedException("invalid token");
            }
        }
        tenant.setKeycloakId(getKeycloakUserId());
        if (!Boolean.TRUE.equals(tenant.getFranceConnect()) && isFranceConnect()) {
            tenant.setFranceConnect(isFranceConnect());
            tenant.setFranceConnectSub(getFranceConnectSub());
            tenant.setFranceConnectBirthCountry(getFranceConnectBirthCountry());
            tenant.setFranceConnectBirthPlace(getFranceConnectBirthPlace());
            tenant.setFranceConnectBirthDate(getFranceConnectBirthDate());

            if (!StringUtils.equals(tenant.getFirstName(), getFirstName())
                    || !StringUtils.equals(tenant.getLastName(), getLastName())
                    || (getPreferredName() != null && !StringUtils.equals(tenant.getPreferredName(), getPreferredName()))) {
                if (tenant.getStatus() == TenantFileStatus.VALIDATED) {
                    documentService.resetValidatedDocumentsStatusOfSpecifiedCategoriesToToProcess(tenant.getDocuments(),
                            Arrays.asList(DocumentCategory.values()));
                }
            }
            tenant.setFirstName(getFirstName());
            tenant.setLastName(getLastName());
            tenant.setPreferredName(getPreferredName() == null? tenant.getPreferredName() : getPreferredName());
            tenantService.updateTenantStatus(tenant);
        }
        return tenantRepository.saveAndFlush(tenant);
    }
}
