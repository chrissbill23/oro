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
}
