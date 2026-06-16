package com.seatrush.virtualuser.competition;

public class VirtualUser {

    private final int number;
    private final String email;
    private final String password;
    private final CompetitionBehavior behavior;
    private CompetitionStatus status;
    private String accessToken;

    public VirtualUser(
            int number,
            String email,
            String password,
            CompetitionBehavior behavior,
            String accessToken
    ) {
        this.number = number;
        this.email = email;
        this.password = password;
        this.behavior = behavior;
        this.status = CompetitionStatus.PREPARING;
        this.accessToken = accessToken;
    }

    public int number() {
        return number;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public CompetitionBehavior behavior() {
        return behavior;
    }

    public CompetitionStatus status() {
        return status;
    }

    public void changeStatus(CompetitionStatus status) {
        this.status = status;
    }

    public String accessToken() {
        return accessToken;
    }

    public void authenticate(String accessToken) {
        this.accessToken = accessToken;
    }
}
