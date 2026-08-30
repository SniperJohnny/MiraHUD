package io.sniperjohnny.github.mirahud.client.hud_for_client;

import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;

import java.util.List;

public final class MediaTypes {

    public record Type(String id, String translationKey) {
    }

    public static final List<Type> ALL = List.of(
            new Type("image", TranslationsKeys.CONFIG_MEDIA_TYPE_IMAGE),
            new Type("video", TranslationsKeys.CONFIG_MEDIA_TYPE_VIDEO)
    );

    private MediaTypes() {
    }

    public static Type byId(String id) {
        for (Type type : ALL) {
            if (type.id().equals(id)) return type;
        }
        return ALL.get(0);
    }
}
