package com.seatrush.virtualuser.account;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/virtual-user-accounts")
public class VirtualUserAccountController {

    private final VirtualUserAccountStore accountStore;

    public VirtualUserAccountController(VirtualUserAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @GetMapping
    public AccountPoolResponseDto getAccountPool() {
        return new AccountPoolResponseDto(accountStore.size());
    }

    public record AccountPoolResponseDto(int storedAccounts) {
    }
}
