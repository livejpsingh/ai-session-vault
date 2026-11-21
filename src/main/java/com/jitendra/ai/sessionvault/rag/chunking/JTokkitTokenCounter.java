package com.jitendra.ai.sessionvault.rag.chunking;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

public final class JTokkitTokenCounter implements TokenCounter {

    private final Encoding encoding;

    public JTokkitTokenCounter(EncodingType encodingType) {
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(encodingType);
    }

    @Override
    public int count(String text) {
        return encoding.countTokens(text);
    }
}
