package fr.dossierfacile.api.front.register.tenant;

import com.google.common.base.Strings;
import fr.dossierfacile.api.front.mapper.TenantMapper;
import fr.dossierfacile.api.front.model.tenant.TenantModel;
import fr.dossierfacile.api.front.register.SaveStep;
import fr.dossierfacile.api.front.register.form.tenant.AccountForm;
import fr.dossierfacile.api.front.service.interfaces.ApartmentSharingService;
import fr.dossierfacile.api.front.service.interfaces.ConfirmationTokenService;
import fr.dossierfacile.api.front.service.interfaces.KeycloakService;
import fr.dossierfacile.api.front.service.interfaces.MailService;
import fr.dossierfacile.api.front.service.interfaces.SourceService;
import fr.dossierfacile.api.front.service.interfaces.TenantService;
import fr.dossierfacile.api.front.service.interfaces.UserRoleService;
import fr.dossierfacile.common.entity.Tenant;
import fr.dossierfacile.common.entity.UserApi;
import fr.dossierfacile.common.repository.TenantCommonRepository;
import fr.dossierfacile.common.service.interfaces.PartnerCallBackService;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Slf4j
public class Account implements SaveStep<AccountForm> {

    private final TenantCommonRepository tenantRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final UserRoleService userRoleService;
    private final TenantMapper tenantMapper;
    private final ApartmentSharingService apartmentSharingService;
    private final TenantService tenantService;
    private final SourceService sourceService;
    private final PartnerCallBackService partnerCallBackService;
    private final MailService mailService;
    private final ConfirmationTokenService confirmationTokenService;
    private final KeycloakService keycloakService;

    @Override
    @Transactional
    public TenantModel saveStep(Tenant t, AccountForm accountForm) {
        String email = accountForm.getEmail().toLowerCase();
        Tenant tenant = tenantRepository.findByEmailAndEnabledFalse(email).orElseGet(() -> tenantService.create(new Tenant(email)));
        tenant.setPassword(bCryptPasswordEncoder.encode(accountForm.getPassword()));
        if (!Strings.isNullOrEmpty(accountForm.getSource())) {
            if (!tenant.getFranceConnect()) {
                tenant.setFirstName(accountForm.getFirstName());
                tenant.setLastName(accountForm.getLastName());
            }
            tenant.setPreferredName(accountForm.getPreferredName());
            tenantRepository.save(tenant);
            UserApi userApi = this.sourceService.findOrCreate(accountForm.getSource());
            partnerCallBackService.registerTenant(accountForm.getInternalPartnerId(), tenant, userApi);
        }
        tenant.setKeycloakId(keycloakService.createKeycloakUserAccountCreation(accountForm, tenant));

        tenantRepository.save(tenant);
        mailService.sendEmailConfirmAccount(tenant, confirmationTokenService.createToken(tenant));
        userRoleService.createRole(tenant);
        apartmentSharingService.resetDossierPdfGenerated(tenant.getApartmentSharing());
        tenant.lastUpdateDateProfile(LocalDateTime.now(), null);
        return tenantMapper.toTenantModel(tenantRepository.save(tenant));
    }
}
