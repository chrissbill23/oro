package com.asata.org.oro.identity.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.dto.MeDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Me", description = "The caller")
public interface MeApi {

    @Operation(summary = "The caller", description = "Its messaging identity, product and organization (Umoja)")
    @GetMapping("/me")
    MeDTO me(@Parameter(hidden = true) Actor actor);

    record EraseRequest(@jakarta.validation.constraints.NotNull String password) {
    }

    @Operation(summary = "Erase my data in Oro", description = "Oro's people only, with their password: their messages "
            + "become deletions, their reactions and files go, they leave every conversation and are signed out of Oro. "
            + "Their Asata account stays (Umoja, Mokili). Umoja's people ask their organization")
    @org.springframework.web.bind.annotation.PostMapping("/me/erase")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    void erase(@Parameter(hidden = true) Actor actor,
               @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody EraseRequest request);
}
