package com.eduvision.oops;

import org.springframework.stereotype.Component;

/** Content processor for rich media - overrides the shared behaviour (polymorphism). */
@Component
public class MediaContentProcessor extends AbstractContentProcessor {

    @Override
    public String kind() {
        return "media";
    }

    @Override
    public String describe(int parts, int questions) {
        return describeWith("streams audio/video around " + parts + " anchored part(s)", parts, questions);
    }
}
