package fr.gouv.bo.controller;

import fr.dossierfacile.common.entity.ApartmentSharing;
import fr.dossierfacile.common.entity.Document;
import fr.dossierfacile.common.entity.DocumentPdfGenerationLog;
import fr.dossierfacile.common.entity.Tenant;
import fr.dossierfacile.common.entity.User;
import fr.dossierfacile.common.entity.UserRole;
import fr.dossierfacile.common.enums.ApplicationType;
import fr.dossierfacile.common.enums.PartnerCallBackType;
import fr.dossierfacile.common.enums.Role;
import fr.dossierfacile.common.repository.DocumentPdfGenerationLogRepository;
import fr.dossierfacile.common.service.interfaces.FileStorageService;
import fr.dossierfacile.common.service.interfaces.PartnerCallBackService;
import fr.gouv.bo.amqp.Producer;
import fr.gouv.bo.dto.BooleanDTO;
import fr.gouv.bo.dto.DeleteUserDTO;
import fr.gouv.bo.dto.EmailDTO;
import fr.gouv.bo.dto.Pager;
import fr.gouv.bo.dto.ReGroupDTO;
import fr.gouv.bo.dto.ResultDTO;
import fr.gouv.bo.dto.UserDTO;
import fr.gouv.bo.model.FileForm;
import fr.gouv.bo.service.ApartmentSharingService;
import fr.gouv.bo.service.DocumentService;
import fr.gouv.bo.service.TenantService;
import fr.gouv.bo.service.UserRoleService;
import fr.gouv.bo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BOController {

    private static final int BUTTONS_TO_SHOW = 5;
    private static final String EMAIL = "email";
    private static final String PAGER = "pager";
    private static final String PAGE_SIZES_STRING = "pageSizes";
    private static final String SELECTED_PAGE_SIZE = "selectedPageSize";
    private static final String REDIRECT_BO_COLOCATION = "redirect:/bo/colocation/";
    private static final String SHOW_ALERT = "showAlert";

    private static final int INITIAL_PAGE = 0;
    private static final int INITIAL_PAGE_SIZE = 100;
    private static final int[] PAGE_SIZES = {100, 200};
    private static final String REDIRECT_BO = "redirect:/bo";

    private final ApartmentSharingService apartmentSharingService;
    private final TenantService tenantService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final Producer producer;
    private final UserRoleService userRoleService;
    private final PartnerCallBackService partnerCallBackService;
    private final DocumentPdfGenerationLogRepository documentPdfGenerationLogRepository;

    @GetMapping("/")
    public String index(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        } else {
            return REDIRECT_BO;
        }
    }

    @GetMapping("/callback/manually/{userApiId}")
    public String callBackManuallyToUserApi(@PathVariable("userApiId") Long userApiId) {
        tenantService.sendCallBacksManuallyToUserApi(userApiId);
        return REDIRECT_BO;
    }

    @GetMapping("/callback/manually/locserviceids")
    public String callBackManuallyToLocserviceIds() {
        tenantService.callBackManuallyToLocserviceIds();
        return REDIRECT_BO;
    }

    @GetMapping("/computeStatusOfAllTenants")
    public String computeStatusOfAllTenants() {
        tenantService.computeStatusOfAllTenants();
        return REDIRECT_BO;
    }

    @GetMapping("/bo/documentFailedList")
    public String documentFailedList(Model model ,@RequestParam("pageSize") Optional<Integer> pageSize, @RequestParam("page") Optional<Integer> page){
        EmailDTO emailDTO = new EmailDTO();
        int evalPageSize = pageSize.orElse(INITIAL_PAGE_SIZE);
        int val = 0;
        if (page.isPresent()) {
            val = page.get() - 1;
        }
        int evalPage = (page.orElse(0) < 1) ? INITIAL_PAGE : val;
        Page<Tenant> tenants = tenantService.getAllTenantsWithFailedGeneratedPdfDocument(PageRequest.of(evalPage, evalPageSize));
        Pager pager = new Pager(tenants.getTotalPages(), tenants.getNumber(), BUTTONS_TO_SHOW);
        model.addAttribute(EMAIL, emailDTO);
        model.addAttribute(PAGER, pager);
        model.addAttribute(PAGE_SIZES_STRING, PAGE_SIZES);
        model.addAttribute(SELECTED_PAGE_SIZE, evalPageSize);
        model.addAttribute("tenantList",tenants);
        return "bo/failed-pdf-tenant";
    }

    @GetMapping("/bo")
    public String bo(@ModelAttribute("numberOfDocumentsToProcess") ResultDTO numberOfDocumentsToProcess, Model model, @RequestParam("pageSize") Optional<Integer> pageSize, @RequestParam("page") Optional<Integer> page, Principal principal) {
        int evalPageSize = pageSize.orElse(INITIAL_PAGE_SIZE);
        int val = 0;
        if (page.isPresent()) {
            val = page.get() - 1;
        }
        int evalPage = (page.orElse(0) < 1) ? INITIAL_PAGE : val;
        EmailDTO emailDTO = new EmailDTO();
        Page<Tenant> tenants = tenantService.listTenantsToProcess(PageRequest.of(evalPage, evalPageSize));
        Pager pager = new Pager(tenants.getTotalPages(), tenants.getNumber(), BUTTONS_TO_SHOW);
        User login_user = userService.findUserByEmail(principal.getName());
        boolean is_admin = login_user.getUserRoles().stream().anyMatch(userRole -> userRole.getRole().name().equals(Role.ROLE_ADMIN.name()));
        model.addAttribute("numberOfTenantsToProcess", tenantService.countTenantsWithStatusInToProcess());
        long result = 0;
        if (numberOfDocumentsToProcess.getId() == null) {
            result = tenantService.getTotalOfTenantsWithFailedGeneratedPdfDocument();
        }
        model.addAttribute("TenantsWithFailedGeneratedPdf", result);
        model.addAttribute("loginUser", is_admin);
        model.addAttribute("tenants", tenants);
        model.addAttribute(SELECTED_PAGE_SIZE, evalPageSize);
        model.addAttribute(PAGE_SIZES_STRING, PAGE_SIZES);
        model.addAttribute(PAGER, pager);
        model.addAttribute(EMAIL, emailDTO);
        return "bo/index";
    }

    @GetMapping("/bo/create/admin")
    public String getAdmin(Model model) {
        EmailDTO emailDTO1 = new EmailDTO();
        model.addAttribute(EMAIL, emailDTO1);
        return "bo/create-admin";
    }

    @GetMapping("/bo/regroup")
    public String getRegroupTenants(RedirectAttributes redirectAttributes,Model model, ReGroupDTO reGroupDTO, @ModelAttribute("showAlert") BooleanDTO booleanDTO) {
        EmailDTO emailDTO1 = new EmailDTO();
        model.addAttribute(EMAIL, emailDTO1);
        model.addAttribute("reGroupData", reGroupDTO);
        if(booleanDTO.isAlertValue()){
            redirectAttributes.addFlashAttribute(SHOW_ALERT, booleanDTO);
            redirectAttributes.addFlashAttribute("alertShow", "alertShow");
        }
        model.addAttribute(SHOW_ALERT, booleanDTO);
        return "bo/regroup-tenants";
    }

    @PostMapping("/bo/regroup/tenant")
    public String regroupTenants(@ModelAttribute("reGroupData") ReGroupDTO reGroupDTO, RedirectAttributes redirectAttributes) {

        if (!reGroupDTO.getTenantEmailCreate().equals(reGroupDTO.getTenantEmailJoin())) {
            Tenant tenantCreate = tenantService.getTenantByEmail(reGroupDTO.getTenantEmailCreate());
            Tenant tenantJoin = tenantService.getTenantByEmail(reGroupDTO.getTenantEmailJoin());
            if (tenantCreate != null && tenantJoin != null) {
                ApartmentSharing apartCreate = tenantCreate.getApartmentSharing();
                ApartmentSharing apartJoin = tenantJoin.getApartmentSharing();

                if (apartCreate.getNumberOfTenants() == 1 && apartCreate.getApplicationType() == ApplicationType.ALONE
                    && apartJoin.getNumberOfTenants() == 1 && apartJoin.getApplicationType() == ApplicationType.ALONE) {

                    tenantService.regroupTenant(tenantJoin, apartCreate, reGroupDTO.getApplicationType());

                    partnerCallBackService.sendCallBack(apartCreate.getTenants(), PartnerCallBackType.MERGED_ACCOUNT);

                    return REDIRECT_BO_COLOCATION + apartCreate.getId() + "#tenant" + tenantCreate.getId();
                }
            }
        }

        BooleanDTO booleanDTO = new BooleanDTO();
        booleanDTO.setAlertValue(true);
        redirectAttributes.addFlashAttribute(SHOW_ALERT, booleanDTO);
        return "redirect:/bo/regroup/";
    }

    @PostMapping("/bo/create/admin")
    public String createOrUpdateUserToAdmin(EmailDTO emailDTO, Model model, @RequestParam(name = "action") String create_user) {
        EmailDTO emailDTO1 = new EmailDTO();
        User user = userService.findUserByEmail(emailDTO.getEmail());
        if (user != null) {
            if (user.getUserRoles().isEmpty()) {
                userRoleService.createRoleAdminByEmail(emailDTO.getEmail(), user, create_user);
            } else {
                UserRole userRole1;
                if (create_user.equals("create_admin")) {
                    userRole1 = user.getUserRoles().stream().filter(userRole -> userRole.getRole().name().equals(Role.ROLE_ADMIN.name())).findFirst().orElse(null);
                } else {
                    userRole1 = user.getUserRoles().stream().filter(userRole -> userRole.getRole().name().equals(Role.ROLE_OPERATOR.name())).findFirst().orElse(null);
                }
                if (userRole1 == null) {
                    userRoleService.createRoleAdminByEmail(emailDTO.getEmail(), user, create_user);
                }
            }
        } else {
            UserDTO userDTO = new UserDTO();
            userDTO.setEmail(emailDTO.getEmail());
            userService.save(userDTO);
            userRoleService.createRoleAdminByEmail(userDTO.getEmail(), null, create_user);
        }

        model.addAttribute(EMAIL, emailDTO1);
        return "bo/create-admin";
    }

    @GetMapping("/bo/searchTenant")
    public String searchTenant(Model model, EmailDTO emailDTO, RedirectAttributes redirectAttributes) {

        List<Tenant> tenantList = tenantService.getTenantByIdOrEmail(emailDTO);

        if (tenantList.isEmpty() || emailDTO.getEmail().isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "No tenant by that name !");
            redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
            return REDIRECT_BO;
        }

        if (tenantList.get(0) == null) {
            redirectAttributes.addFlashAttribute("message", "Tenant not found !");
            redirectAttributes.addFlashAttribute("alertClass", "alert-danger");
            return REDIRECT_BO;
        }

        if (emailDTO.getEmail().contains("@") || StringUtils.isNumeric(emailDTO.getEmail())) {
            return REDIRECT_BO_COLOCATION + tenantList.get(0).getApartmentSharing().getId();
        }

        EmailDTO emailDTOSave = new EmailDTO();
        model.addAttribute(EMAIL, emailDTOSave);
        model.addAttribute("matchList", tenantList);
        model.addAttribute("keySearch", emailDTO.getEmail());

        return "bo/search";

    }

    @GetMapping("/bo/searchResult")
    public String searchResult(Model model, @RequestParam(value = "q", defaultValue = "") String q,
                               @RequestParam("pageSize") Optional<Integer> pageSize, @RequestParam("page") Optional<Integer> page) {
        int evalPageSize = pageSize.orElse(INITIAL_PAGE_SIZE);
        int val = 0;
        if (page.isPresent()) {
            val = page.get() - 1;
        }
        int evalPage = (page.orElse(0) < 1) ? INITIAL_PAGE : val;
        Page<Tenant> tenants = tenantService.listTenantsFilter(PageRequest.of(evalPage, evalPageSize), q);
        Pager pager = new Pager(tenants.getTotalPages(), tenants.getNumber(), BUTTONS_TO_SHOW);
        model.addAttribute("tenants", tenants);
        model.addAttribute(SELECTED_PAGE_SIZE, evalPageSize);
        model.addAttribute(PAGE_SIZES_STRING, PAGE_SIZES);
        model.addAttribute(PAGER, pager);
        return "bo/searchResult";
    }

    @GetMapping("/bo/nextApplication")
    public String nextApplication(Principal principal, @RequestParam(value = "tenant_id", required = false) Long tenantId) {
        if (principal == null) {
            return "redirect:/error";
        }
        return tenantService.redirectToApplication(principal, tenantId);
    }

    @GetMapping("/bo/deleteAccount")
    public String getDeleteAccount(Model model) {
        DeleteUserDTO deleteUser = new DeleteUserDTO();
        model.addAttribute("deleteUser", deleteUser);
        return "bo/delete-account";
    }

    @PostMapping("/bo/deleteAccount")
    public String postDeleteAccount(@Validated @ModelAttribute("deleteUser") DeleteUserDTO deleteUser, BindingResult result, Principal principal) {
        if (result.hasErrors()) {
            return "bo/delete-account";
        }
        User user = userService.findUserByEmail(deleteUser.getEmail());
        if (user instanceof Tenant) {
            Tenant tenant = (Tenant) user;
            partnerCallBackService.sendCallBack(tenant, PartnerCallBackType.DELETED_ACCOUNT);
            apartmentSharingService.delete(tenant.getApartmentSharing());
        } else {
            if (!principal.getName().equals(deleteUser.getEmail())) {
                userService.delete(user.getId());
            }
        }
        return "redirect:/bo/deleteAccount";
    }


    @GetMapping("/bo/deleteFile")
    public String deleteFileForm(Model model) {
        FileForm fileForm = new FileForm();
        model.addAttribute("fileForm", fileForm);
        return "bo/delete-file";
    }

    @PostMapping("/bo/deleteFile")
    public String deleteFileProcess(@Valid @ModelAttribute FileForm fileForm, BindingResult result) {
        if (result.hasErrors()) {
            return "bo/deleteFile";
        }
        fileStorageService.deleteAllFiles(fileForm.getPath());
        return "redirect:/bo/deleteFile";
    }

    @GetMapping("/bo/regeneratePdfDocument/{id}")
    public String regeneratePdfDocument(@PathVariable Long id) {
        Document document = documentService.findDocumentById(id);
        documentService.initializeFieldsToProcessPdfGeneration(document);
        producer.generatePdf(id,
                documentPdfGenerationLogRepository.save(DocumentPdfGenerationLog.builder()
                        .documentId(id)
                        .build()).getId());
        Tenant tenant = document.getTenant() != null ? document.getTenant() : document.getGuarantor().getTenant();
        long apartmentSharingId = tenant.getApartmentSharing().getId();
        return REDIRECT_BO_COLOCATION + apartmentSharingId + "#tenant" + tenant.getId();
    }

    @PostMapping("/bo/regeneratePdfDocument")
    public String regeneratePdfDocument(RedirectAttributes redirectAttributes, @ModelAttribute("mapping1Form") ResultDTO numberOfDocumentsToProcess) {
        documentService.regenerateFailedPdfDocumentsUsingButtonRequest();
        numberOfDocumentsToProcess.setId(0L);
        redirectAttributes.addFlashAttribute("numberOfDocumentsToProcess", numberOfDocumentsToProcess);
        return REDIRECT_BO;
    }

    @Scheduled(cron = "0 1 0 * * ?")
    public void regenerateFailedPdfDocumentsOneDayAgoTask() {
        log.info("Checking for failed PDF generation");
        documentService.regenerateFailedPdfDocumentsOneDayAgoUsingScheduledTask();
    }

    @PostMapping(value = "/bo/regenerateStatusOfTenants")
    public String regenerateStatusOfTenants(@RequestParam("email") String email) {
        tenantService.updateStatusOfSomeTenants(email);
        return REDIRECT_BO;
    }

    @GetMapping("/bo/computeTenantsStatus")
    public String computeTenantStatus(Model model) {
        EmailDTO emailDTO = new EmailDTO();
        model.addAttribute("tenantList", emailDTO);
        model.addAttribute(EMAIL, emailDTO);
        return "bo/compute-status";
    }

    @GetMapping("/bo/deleteAccountsNotProperlyDeleted")
    public String deleteAccountsNotProperlyDeleted() {
        tenantService.deleteAccountsNotProperlyDeleted();
        return REDIRECT_BO;
    }

    @GetMapping("/update/documents/creationDate")
    public String updateDocumentsWithNullCreationDate() {
        tenantService.updateDocumentsWithNullCreationDateTime();
        return REDIRECT_BO;
    }
}