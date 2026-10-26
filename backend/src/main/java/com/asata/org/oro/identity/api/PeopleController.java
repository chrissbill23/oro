package com.asata.org.oro.identity.api;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.identity.service.PeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "People", description = "The names of the people of one's conversations")
@RestController
@RequiredArgsConstructor
public class PeopleController {

    private final PeopleService people;

    @Operation(summary = "The names of people", description = "Oro's people only: for each user id (at most 200, "
            + "comma separated), its given name, surname and username, when it is the caller or someone of the caller's "
            + "conversations; the others are left out")
    @GetMapping("/people")
    public List<PeopleService.Person> people(@Parameter(hidden = true) Actor actor, @RequestParam List<String> userIds) {
        return people.people(actor, userIds);
    }
}
