package com.amplifiers.pathfinder.entity.enrollment;

import com.amplifiers.pathfinder.entity.gig.Gig;
import com.amplifiers.pathfinder.entity.user.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "enrollment")
public class Enrollment {
    @Id
    @GeneratedValue
    private Integer id;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gigId")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Gig gig;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User buyer;

    private OffsetDateTime createdAt;

    // starts when the buyer confirms it.
    private OffsetDateTime startedAt;

    @NotNull(message = "Deadline is required.")
    @Future(message = "Deadline must be in the future.")
    private OffsetDateTime  deadline;

    private OffsetDateTime completedAt;

    @NotNull(message = "Price is required.")
    private float price;

    @NotNull(message = "Number of sessions is required.")
    private Integer numSessions;

    private Integer numSessionsCompleted;

    @NotNull(message = "Session duration is required.")
    private Integer sessionDurationInMinutes;

    private boolean buyerConfirmed; // when buyer accepts the offer
    private boolean paid;

    // TODO: add an enrollment offer description field.
}
