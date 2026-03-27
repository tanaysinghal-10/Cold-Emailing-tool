package com.coldbot.job.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "email_rate_limits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "emails_sent", nullable = false)
    @Builder.Default
    private Integer emailsSent = 0;

    @Column(name = "daily_limit", nullable = false)
    @Builder.Default
    private Integer dailyLimit = 10;
}
