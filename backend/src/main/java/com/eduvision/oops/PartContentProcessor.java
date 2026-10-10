package com.eduvision.oops;

import org.springframework.stereotype.Component;

/** Content processor for tappable AR parts - overrides the shared behaviour (polymorphism). */
@Component
public class PartContentProcessor extends AbstractContentProcessor {

    @Override
    public String kind() {
        return "part";
    }

    @Override
    public String describe(int parts, int questions) {
        return describeWith("exposes " + parts + " tappable AR parts", parts, questions);
    }
}
