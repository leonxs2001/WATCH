package de.thb.kritis_elfe.entity;

import de.thb.kritis_elfe.entity.questionnaire.Questionnaire;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    private LocalDateTime date;

    @OneToMany(mappedBy = "report")
    private List<Questionnaire> questionnaires;

    public String getDateAsString(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("HH:mm");

        String formattedDate = date.format(formatter);
        String formattedTime = date.format(formatter2);
        return "am " + formattedDate + " um " + formattedTime + " Uhr";
    }
}
