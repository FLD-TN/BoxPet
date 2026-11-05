package com.dex.boxpet.pet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 0.4.1 pet name rules: colours and gradients for everyone, no placeholders, no unsafe tags. */
class NameRulesTest {

    @Test
    void coloursAndGradientsAreAllowed() {
        assertTrue(PetActions.validName("Rong Nho"));
        assertTrue(PetActions.validName("&aRong &bNho"));
        assertTrue(PetActions.validName("&#FF8A8ARong"));
        assertTrue(PetActions.validName("<#FF8A8A>Rong</#FF8A8A>"));
        assertTrue(PetActions.validName("<gradient:#FF8A8A:#7FDBFF>Rong Nho</gradient>"));
        assertTrue(PetActions.validName("<gradient:red:blue>Rong</gradient>"));
        assertTrue(PetActions.validName("<rainbow>Rong</rainbow>"));
        assertTrue(PetActions.validName("<bold><yellow>Vang</yellow></bold>"));
        assertTrue(PetActions.validName("<color:#FFD166>Vang"));
    }

    @Test
    void placeholdersAndStrayHashAreRefused() {
        assertFalse(PetActions.validName("%player_name%"));
        assertFalse(PetActions.validName("Rong%"));
        assertFalse(PetActions.validName("#1 Rong"));
        assertFalse(PetActions.validName("Rong #FF8A8A"));
    }

    @Test
    void unsafeTagsAreRefused() {
        assertFalse(PetActions.validName("<click:run_command:/op me>Rong"));
        assertFalse(PetActions.validName("<hover:show_text:'x'>Rong"));
        assertFalse(PetActions.validName("<obfuscated>Rong"));
        assertFalse(PetActions.validName("&kRong"));
        assertFalse(PetActions.validName("<font:uniform>Rong"));
        assertFalse(PetActions.validName("Rong<newline>Nho"));
        assertFalse(PetActions.validName("<selector:@a>"));
    }
}
