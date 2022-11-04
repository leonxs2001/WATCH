package de.thb.webbaki.service;

import de.thb.webbaki.controller.form.UserForm;
import de.thb.webbaki.controller.form.UserRegisterFormModel;
import de.thb.webbaki.controller.form.UserToRoleFormModel;
import de.thb.webbaki.entity.Role;
import de.thb.webbaki.entity.User;
import de.thb.webbaki.mail.EmailSender;
import de.thb.webbaki.mail.Templates.AdminNotifications.*;
import de.thb.webbaki.mail.Templates.UserNotifications.*;
import de.thb.webbaki.mail.confirmation.ConfirmationToken;
import de.thb.webbaki.mail.confirmation.ConfirmationTokenService;
import de.thb.webbaki.repository.RoleRepository;
import de.thb.webbaki.repository.UserRepository;
import de.thb.webbaki.service.Exceptions.UserAlreadyExistsException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

import javax.transaction.Transactional;
import java.time.LocalDateTime;


@Builder
@AllArgsConstructor
@Service
@Transactional
@Slf4j
public class UserService {
    private UserRepository userRepository; ////initialize repository Object
    private RoleRepository roleRepository;
    private RoleService roleService;
    private PasswordEncoder passwordEncoder;
    private ConfirmationTokenService confirmationTokenService;
    private EmailSender emailSender;
    private SectorService sectorService;

    //Repo Methods --------------------------
    public List<User> getAllUsers() {
        return (List<User>) userRepository.findAll();
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getUsersByBranche(String branche) {
        return userRepository.findAllByBranche(branche);
    }

    public List<User> getUsersBySector(String sector) {
        return userRepository.findAllBySector(sector);
    }

    public Boolean usernameExists(String username) {
        return userRepository.findByUsername(username) != null;
    }

    public List<User> getUserByAdminrole() {
        return userRepository.findByRoles_Name("ROLE_SUPERADMIN");
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getUserByOfficeRole() {
        return userRepository.findByRoles_Name("ROLE_GESCHÄFTSSTELLE");
    }

    /**
     * @param user is used to create new user -> forwarded to registerNewUser
     * @return newly created token
     */
    public String createToken(User user) {

        String token = UUID.randomUUID().toString();

        ConfirmationToken confirmationToken = new ConfirmationToken(
                token, LocalDateTime.now(), LocalDateTime.now().plusDays(3), user);

        confirmationTokenService.saveConfirmationToken(confirmationToken);
        return token;
    }

    /**
     * Registering new User with all parameters from User.java
     * Using emailExists() to check whether user already exists
     */
    public void registerNewUser(final UserRegisterFormModel form) throws UserAlreadyExistsException {
        if (usernameExists(form.getUsername())) {
            throw new UserAlreadyExistsException("Es existiert bereits ein Account mit folgender Email-Adresse: " + form.getEmail());
        } else {

            final User user = new User();

            user.setLastName(form.getLastname());
            user.setFirstName(form.getFirstname());
            user.setBranche(form.getBranche());
            user.setSector(sectorService.getSectorByBrancheName(form.getBranche()).getName());
            user.setCompany(form.getCompany());
            user.setPassword(passwordEncoder.encode(form.getPassword()));
            user.setEmail(form.getEmail());
            //set the role to "Geschäftsstelle" if this Branche is choosen
            if(form.getBranche().equals("Geschäftsstelle")){
                user.setRoles(Arrays.asList(roleRepository.findByName("ROLE_GESCHÄFTSSTELLE")));
            }else {
                user.setRoles(Arrays.asList(roleRepository.findByName("ROLE_KRITIS_BETREIBER")));
            }
            user.setUsername(form.getUsername());
            user.setEnabled(false);

            String token = createToken(user); // To create the token of the user


            String userLink = "https://webbaki.th-brandenburg.de/confirmation/confirmByUser?token=" + token;

            userRepository.save(user);


            /*Outsourcing Mail to thread for speed purposes*/
            new Thread(()->{
                //Email to new registered user
                emailSender.send(form.getEmail(), UserRegisterNotification.buildUserEmail(form.getFirstname(), userLink));
            }).start();

        }
    }

    @Transactional
    public String confirmToken(String token) throws IllegalStateException {
        ConfirmationToken confirmationToken = confirmationTokenService.getConfirmationToken(token);

        LocalDateTime expiredAt = confirmationToken.getExpiresAt();
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("token expired");
        } else {
            confirmationTokenService.setConfirmedAt(token);
            confirmAdmin(token);
        }
        enableUser(confirmationToken.getUser().getUsername(), token);

        return "confirmation/confirm";
    }

    /**
     * Using USERDETAILS -> Enabling User in Spring security
     * User is enabled if user_confirmation && admin_confirmation == TRUE
     *
     * @param username to get the user
     * @param token    to get the according token
     * @return value TRUE or FALSE based on INTEGER value (0 = false, 1 = true)
     */
    public int enableUser(String username, String token) {

        if (confirmationTokenService.getConfirmationToken(token).accessGranted(token)) {
            return userRepository.enableUser(username);
        } else return -1;
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
     * Setting admin_confirmation TRUE or False
     *
     * @param token to get matching ConfirmationToken
     * @return value TRUE or FALSE based on bit value (0 = false, 1 = true)
     */
    public int adminConfirmation(String token) {
        return confirmationTokenService.setConfirmedByAdmin(token);
    }

    /**
     * Setting user_confirmation TRUE or False and getting HTML Page with confirmation Details
     * using method public int userConfirmation(String token)
     *
     * @param token to get matching ConfirmationToken
     * @return value TRUE or FALSE based on bit value (0 = false, 1 = true)
     */
    public String confirmUser(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.getConfirmationToken(token);
        LocalDateTime expiredAt = confirmationToken.getExpiresAt();
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("token expired");
        } else {
            //The confirmation only should be done if it is not already done
            if (!confirmationToken.getUserConfirmation()) {
                userConfirmation(token);

                //send link to admin
                String adminLink = "https://webbaki.th-brandenburg.de/confirmation/confirm?token=" + token;
                User user = confirmationToken.getUser();

                /* Outsourcing Mailsending to thread for speed purposes */
                new Thread(() -> {
                    for (User officeAdmin : getUserByOfficeRole()) {
                        emailSender.send(officeAdmin.getEmail(), AdminRegisterNotification.buildAdminEmail(officeAdmin.getFirstName(), adminLink,
                                user.getFirstName(), user.getLastName(),
                                user.getEmail(), user.getBranche(), user.getCompany()));
                    }
                }).start();
            }
        }
        return "confirmation/confirmedByUser";
    }

    /**
     * Setting admin_confirmation TRUE or False and getting HTML Page with confirmation Details
     * using method public int adminConfirmation(String token)
     *
     * @param token to get matching ConfirmationToken
     * @return value TRUE or FALSE based on bit value (0 = false, 1 = true)
     */
    public String confirmAdmin(String token) {
        adminConfirmation(token);
        return "confirmation/confirm";
    }

    public void setCurrentLogin(User u) {
        u.setLastLogin(LocalDateTime.now());
        userRepository.save(u);
    }

    /**
     * USED IN SUPERADMIN DASHBOARD
     * Superadmin can add Roles to specific Users
     *
     * @param userToRoleFormModel to get Userdata, especially roles from user
     */
    public void addAndDeleteRoles(UserToRoleFormModel userToRoleFormModel) {

        for (int i = 0; i < userToRoleFormModel.getUsers().size(); i++) {

            User user = getUserByUsername(userToRoleFormModel.getUsers().get(i).getUsername());

            String roleString = userToRoleFormModel.getRole().get(i);
            String roleDelString = userToRoleFormModel.getRoleDel().get(i);

            if (!roleString.equals("none")) {
                Role role = roleService.getRoleByName(roleString);
                user.getRoles().add(role);

                /*Outsourcing Mail to thread for speed purposes*/
                new Thread(() -> {
                    emailSender.send(user.getEmail(), UserAddRoleNotification.changeRoleMail(user.getFirstName(),
                            user.getLastName(),
                            role));

                    for (User superAdmin : getUserByAdminrole()) {
                        emailSender.send(superAdmin.getEmail(), AdminAddRoleNotification.changeRole(superAdmin.getFirstName(),
                                superAdmin.getLastName(),
                                role, user.getUsername()));
                    }
                }).start();
            }

            if (!roleDelString.equals("none")) {
                Role roleDel = roleService.getRoleByName(roleDelString);
                user.getRoles().remove(roleDel);

                /*Outsourcing Mail to thread for speed purposes*/
                new Thread(() -> {
                    emailSender.send(user.getEmail(), UserRemoveRoleNotification.removeRoleMail(user.getFirstName(),
                            user.getLastName(),
                            roleDel));

                    for (User superAdmin : getUserByAdminrole()) {
                        emailSender.send(superAdmin.getEmail(), AdminRemoveRoleNotification.removeRole(superAdmin.getFirstName(),
                                superAdmin.getLastName(),
                                roleDel,
                                user.getUsername()));
                    }
                }).start();
            }

            if (!roleDelString.equals("none") || !roleString.equals("none")) {
                saveUser(user);
            }

        }
    }


    /**
     * Enable/Disable user to give access to WebBakI
     *
     * @param form to get userlist
     */
    public void changeEnabledStatus(UserForm form) {

        List<User> users = getAllUsers();

        /*Outsourcing Mail to thread for speed purposes*/
        new Thread(() -> {
            for (int i = 0; i < users.size(); i++) {

                if (users.get(i).isEnabled() != (form.getUsers().get(i).isEnabled())) {
                    users.get(i).setEnabled(form.getUsers().get(i).isEnabled());


                    emailSender.send(users.get(i).getEmail(), UserChangeEnabledStatusNotification.changeBrancheMail(users.get(i).getFirstName(), users.get(i).getLastName()));

                    for (User officeAdmin : getUserByOfficeRole()) {
                        emailSender.send(officeAdmin.getEmail(), AdminDeactivateUserSubmit.changeEnabledStatus(officeAdmin.getFirstName(),
                                officeAdmin.getLastName(),
                                users.get(i).isEnabled(),
                                users.get(i).getUsername()));
                    }
                }
            }
        }).start();
    }

    /**
     * Change Branche of User
     *
     * @param form to get Branche
     */
    public void changeBranche(UserForm form) {

        for (int i = 0; i < form.getUsers().size(); i++) {

            User user = getUserByUsername(form.getUsers().get(i).getUsername());
            User updatedUser = form.getUsers().get(i);

            if (!user.getBranche().equals(updatedUser.getBranche())) {
                userRepository.save(updatedUser);

                /*
                 * Outsourcing Email sending cause of speed
                 */
                new Thread(() -> {
                    emailSender.send(updatedUser.getEmail(), UserChangeBrancheNotification.changeBrancheMail(updatedUser.getFirstName(),
                            updatedUser.getLastName(),
                            updatedUser.getBranche()));

                    for (User officeAdmin : getUserByOfficeRole()) {
                        emailSender.send(officeAdmin.getEmail(), AdminChangeBrancheSubmit.changeBrancheMail(officeAdmin.getFirstName(),
                                officeAdmin.getLastName(),
                                updatedUser.getBranche(),
                                updatedUser.getUsername()));
                    }
                }).start();

            }
        }
    }
}
