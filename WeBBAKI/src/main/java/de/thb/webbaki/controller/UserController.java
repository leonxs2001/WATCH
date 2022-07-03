package de.thb.webbaki.controller;

import de.thb.webbaki.controller.form.UserRegisterFormModel;
import de.thb.webbaki.entity.Questionnaire;
import de.thb.webbaki.entity.User;
import de.thb.webbaki.repository.QuestionnaireRepository;
import de.thb.webbaki.security.MyUserDetails;
import de.thb.webbaki.security.MyUserDetailsService;
import de.thb.webbaki.service.Exceptions.UserAlreadyExistsException;
import de.thb.webbaki.service.QuestionnaireService;
import de.thb.webbaki.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@AllArgsConstructor
public class UserController {
    private final UserService userService;
    private MyUserDetailsService myUserDetailsService;
    private QuestionnaireService questionnaireService;
    private QuestionnaireRepository questionnaireRepository;

    //private final ReportService reportService;
    //private final ProviderService providerService;


    @Deprecated
    @GetMapping("users")
    public String showUsers(Model model) {
        List<User> userList = userService.getAllUsers();
        model.addAttribute("userList", userList);
        return "users";
    }

    @Deprecated
    @PostMapping("users")
    public String addUser(@Valid UserRegisterFormModel form,
                              BindingResult result) {
        //if (result.hasErrors()) ... TODO: add error messages here
        userService.addUser(
                form.getLastname(),
                form.getFirstname(),
                form.getBranche(),
                form.getCompany() ,
                "geheimesPassword",
                form.getEmail() ,
                false);


        return "redirect:/users";
    }

    @GetMapping("register/user")
    public String showRegisterForm(Model model){
        UserRegisterFormModel formModel = new UserRegisterFormModel();
        model.addAttribute("user",formModel);
        return "register/user_registration";
    }

    @PostMapping("register/user")
    public String registerUser(
            @ModelAttribute("user") @Valid UserRegisterFormModel formModel, BindingResult result,
            Model model){

        if(result.hasErrors()){
            return "register/user_registration";
        }

        try {
            userService.registerNewUser(formModel);
            Questionnaire q = new Questionnaire();
            q.setUser(userService.getUserByEmail(formModel.getEmail()));
            q.setDate(LocalDateTime.now());
            q.setMapping("{1=none;none, 2=none;none, 3=none;none, 4=none;none, 5=none;none, 6=none;none, 7=none;none, 8=none;none, 9=none;none, 10=none;none, 11=none;none, 12=none;none, 13=none;none, 14=none;none, 15=none;none, 16=none;none, 17=none;none, 18=none;none, 19=none;none, 20=none;none, 21=none;none, 22=none;none, 23=none;none, 24=none;none, 25=none;none, 26=none;none, 27=none;none}");
            questionnaireRepository.save(q);

        } catch (UserAlreadyExistsException uaeEx) {
            model.addAttribute("emailError", "Es existiert bereits ein Account mit dieser Email-Adresse.");
            return "register/user_registration";
        }

        return "register/success_register";
    }


    @GetMapping("/account/user_details")
    public String showUserData(Authentication authentication, Model model) {

            User user = userService.getUserByEmail(authentication.getName());
            model.addAttribute("user", user);
            


            return "/account/user_details";
    }

    @GetMapping("/data/user/reports")
    public String showCustomerOrders(Authentication authentication, Model model) {

        /* REPORTS
        userService.getUserByEmail(authentication.getName()).ifPresent(
                user -> {
                    final var reportList = reportService.getAllReportsByUser(user.getId());
                    model.addAttribute("reportList", reportList);
                }
        );
        */

        return "account/user_reports";
    }
/*
    @GetMapping("/data/customer/orders/details/{orderID}")
    public String showCustomerOrdersDetail(@PathVariable("orderID") long orderID, Model model) {

        Order order = orderService.findOrder(orderID);
        model.addAttribute("order", order);

        List<Provider> providers = new ArrayList<>();


        order.getMatchingProviders().forEach((id) -> providerService.getProvider(id).ifPresent(providers::add));
        model.addAttribute("providers", providers);



        return "account/customer_order_data_details";
    }
*/
    /*
    @PostMapping("/approve_provider")
    public String approveProvider(
            @ModelAttribute(name = "orderId") long orderId,
            @ModelAttribute(name = "providerId") long providerId,
            RedirectAttributes redirectAttrs) {
        orderService.commissionOrder(orderId, providerId);

        redirectAttrs.addFlashAttribute("success", "Der Provider wurde beauftragt!");
        return "redirect:data/customer/orders";
    }
*/

}





