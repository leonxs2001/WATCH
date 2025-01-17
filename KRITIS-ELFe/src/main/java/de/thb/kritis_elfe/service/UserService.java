package de.thb.kritis_elfe.service;

import de.thb.kritis_elfe.configuration.KritisElfeReader;
import de.thb.kritis_elfe.controller.form.ChangeCredentialsForm;
import de.thb.kritis_elfe.controller.form.UserFormModel;
import de.thb.kritis_elfe.controller.form.UserRegisterFormModel;
import de.thb.kritis_elfe.entity.*;
import de.thb.kritis_elfe.mail.EmailSender;
import de.thb.kritis_elfe.repository.RoleRepository;
import de.thb.kritis_elfe.repository.UserRepository;
import de.thb.kritis_elfe.service.Exceptions.AccessDeniedException;
import de.thb.kritis_elfe.service.Exceptions.EmailNotMatchingException;
import de.thb.kritis_elfe.service.Exceptions.PasswordNotMatchingException;
import de.thb.kritis_elfe.service.Exceptions.UserAlreadyExistsException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.util.*;

import javax.transaction.Transactional;
import java.time.LocalDateTime;


@Builder
@AllArgsConstructor
@Service
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailSender emailSender;
    private final BranchService branchService;
    private final KritisElfeReader kritisElfeReader;
    private final FederalStateService federalStateService;

    //Repo Methods --------------------------
    public List<User> getAllUsers() {
        return (List<User>) userRepository.findAll();
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Boolean usernameExists(String username) {
        return userRepository.findByUsername(username) != null;
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getUserByOfficeRole() {
        return userRepository.findByRoles_Name("ROLE_GESCHÄFTSSTELLE");
    }

    public User createUser(User user){
        return userRepository.save(user);
    }

    /**
     * Creates the token for the given user and save it.
     * @param user r
     * @return newly created token as String
     */
    public String createToken(User user) {

        String token = UUID.randomUUID().toString();

        ConfirmationToken confirmationToken = new ConfirmationToken(
                token, LocalDateTime.now(), LocalDateTime.now().plusDays(3), user);

        confirmationTokenService.saveConfirmationToken(confirmationToken);
        return token;
    }

    /**
     * Registering and creating a new User.
     * @param form
     * @throws UserAlreadyExistsException
     */
    public void registerNewUser(final UserRegisterFormModel form) throws UserAlreadyExistsException {
        if (usernameExists(form.getUsername())) {
            throw new UserAlreadyExistsException();
        }

        User user = User.builder().
                username(form.getUsername()).
                email(form.getEmail()).
                enabled(false).
                password(passwordEncoder.encode(form.getPassword())).
                roles(Collections.singletonList(form.getRole())).build();

        if(form.getRole().getName().equals("ROLE_LAND")){
            user.setFederalState(form.getFederalState());
        }else if(form.getRole().getName().equals("ROLE_RESSORT")){
            user.setRessort(form.getRessort());
        }

        String token = createToken(user); // To create the token of the user


        String userLink = kritisElfeReader.getUrl() + "bestätigung/nutzer?token=" + token;

        userRepository.save(user);

        Context registrationContext = new Context();
        registrationContext.setVariable("username", user.getUsername());
        registrationContext.setVariable("link", userLink);
        emailSender.sendMailFromTemplate("/mail/user_registration", registrationContext, user.getEmail());


    }

    /**
     * Confirm created userconfirmation token
     * @param token
     * @return
     * @throws IllegalStateException
     */
    @Transactional
    public void confirmTokenByOffice(String token) throws IllegalStateException {

        ConfirmationToken confirmationToken = confirmationTokenService.getConfirmationToken(token);
        User user = confirmationToken.getUser();

        LocalDateTime expiredAt = confirmationToken.getExpiresAt();
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("token expired");
        } else {
            confirmationToken.setConfirmedAt(LocalDateTime.now());
            confirmationToken.setAdminConfirmation(true);
            confirmationTokenService.saveConfirmationToken(confirmationToken);
        }
        if (confirmationToken.getUserConfirmation()) {
            user.setEnabled(true);
            userRepository.save(user);
        }

        Context enabledContext = new Context();
        enabledContext.setVariable("username", user.getUsername());
        enabledContext.setVariable("link", kritisElfeReader.getUrl() + "login");
        emailSender.sendMailFromTemplate("/mail/user_enabled", enabledContext, user.getEmail());

    }

    /**
     * Setting user_confirmation TRUE or False
     *
     * @param token to get matching ConfirmationToken
     * @return value TRUE or FALSE based on bit value (0 = false, 1 = true)
     */
    public int userConfirmation(String token) {
        return confirmationTokenService.setConfirmedByUser(token);
    }

    /**
     * Setting user_confirmation TRUE or False and getting HTML Page with confirmation Details
     * using method public int userConfirmation(String token)
     *
     * @param token to get matching ConfirmationToken
     */
    public void confirmUser(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.getConfirmationToken(token);
        LocalDateTime expiredAt = confirmationToken.getExpiresAt();
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("token expired");
        } else {
            //The confirmation only should be done if it is not already done
            if (!confirmationToken.getUserConfirmation()) {
                userConfirmation(token);

                //send link to admin
                String adminLink = kritisElfeReader.getUrl() + "bestätigung/geschäftsstelle?token=" + token;
                User user = confirmationToken.getUser();

                for (User officeUser : getUserByOfficeRole()) {
                    //only send it to enabled users
                    if(officeUser.isEnabled()) {
                        Context officeEnableUserContext = new Context();
                        officeEnableUserContext.setVariable("username", officeUser.getUsername());
                        officeEnableUserContext.setVariable("newUser", user);
                        officeEnableUserContext.setVariable("link", adminLink);

                        emailSender.sendMailFromTemplate("/mail/office_enable_user", officeEnableUserContext, officeUser.getEmail());
                    }
                }
            }
        }
    }

    /**
     * Enable/Disable user to give access to KRITIS-ELFe
     *
     * @param form to get userlist
     */
    public void changeEnabledStatusFromForm(UserFormModel form) {

        List<User> users = getAllUsers();
        List<User> changedUsers = new ArrayList<>();

        for(User user: form.getUsers()){
            User oldUser = users.get(users.indexOf(user));
            if(oldUser.isEnabled() != user.isEnabled()){
                oldUser.setEnabled(user.isEnabled());
                userRepository.save(oldUser);
                changedUsers.add(oldUser);
            }
        }

        for (User user: changedUsers) {
            Context changedEnabledContext = new Context();
            changedEnabledContext.setVariable("user", user);
            emailSender.sendMailFromTemplate("/mail/changed_enabled", changedEnabledContext, user.getEmail());
        }
    }

    /**
     * Let user change the own credentials: password, email
     * @param form
     * @param user
     * @throws PasswordNotMatchingException
     * @throws EmailNotMatchingException
     */
    public void changeCredentials(ChangeCredentialsForm form, User user) throws PasswordNotMatchingException, EmailNotMatchingException {

        if (form.getOldPassword() != null) {
            if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword()) || !form.getNewPassword().equals(form.getConfirmNewPassword())) {
                throw new PasswordNotMatchingException();
            } else if (!form.getOldPassword().equals(form.getNewPassword()) && form.getNewPassword().equals(form.getConfirmNewPassword())) {
                user.setPassword(passwordEncoder.encode(form.getNewPassword()));
            }
        }

        if (form.getOldEmail() != null) {
            if (!form.getOldEmail().equals(user.getEmail())) {
                throw new EmailNotMatchingException();
            }
            else if (!form.getOldEmail().equals(form.getNewEmail())) {
                user.setEmail(form.getNewEmail());
            }
        }

        saveUser(user);
    }

    /**
     * Get the Name of the FederalState or the Ressort of the User.
     * It returns as default the FederalState "Brandenburg", if the user has no Ressort or FederalState.
     * @param user
     * @return name of the FederalState or Ressort
     */
    public String getRessortOrFederalStateName(User user){
        String name;
        Role landRole = roleService.getRoleByName("ROLE_LAND");
        Role ressortRole = roleService.getRoleByName("ROLE_Ressort");

        if(user.getRoles().contains(landRole)){
            name = user.getFederalState().getName();
        }else if(user.getRoles().contains(ressortRole)){
            name = user.getRessort().getName();
        }else{
            name =  federalStateService.getFederalStateByName("Brandenburg").getName();
        }

        return name;
    }

    /**
     * Checks the authorization of the user for the FederalState or Ressort.
     * Throws an AccessDeniedException if the user does not have the authorization
     * @param user
     * @param federalState
     * @param ressort
     * @return
     */
    public void checkAuthorizationOfUserForFederalStateOrRessort(User user, FederalState federalState, Ressort ressort) throws AccessDeniedException {
        Role adminRole = roleService.getRoleByName("ROLE_BBK_ADMIN");
        if(user.getRoles().contains(adminRole)){
            return;
        }

        Role landRole = roleService.getRoleByName("ROLE_LAND");
        if(user.getRoles().contains(landRole) && user.getFederalState().equals(federalState)){
            return;
        }

        Role ressortRole = roleService.getRoleByName("ROLE_RESSORT");
        if(!(user.getRoles().contains(ressortRole) && user.getRessort().equals(ressort))){
            if (federalState != null) {
                throw new AccessDeniedException("The user doesnt have the permission to access the federal State.");
            } else {
                throw new AccessDeniedException("The user doesnt have the permission to access the ressort.");
            }
        }
    }
}
