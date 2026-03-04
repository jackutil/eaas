package dev.jackutil.mapper;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;

public class SubmodelLoader {
    public static Submodel loadSubmodel(String template) {
        try {
            JsonDeserializer jsonDeserializer = new JsonDeserializer();
            return jsonDeserializer.read(template, Submodel.class);
        } catch (DeserializationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Submodel deepCopy(Submodel template) {
        try {
            JsonSerializer serializer = new JsonSerializer();
            String json = serializer.write(template);

            JsonDeserializer deserializer = new JsonDeserializer();
            return deserializer.read(json, Submodel.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deep copy Submodel", e);
        }
    }
}
