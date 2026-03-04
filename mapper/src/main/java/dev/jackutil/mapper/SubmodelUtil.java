package dev.jackutil.mapper;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.eclipse.digitaltwin.aas4j.v3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static dev.jackutil.mapper.SubmodelLoader.deepCopy;

public class SubmodelUtil {

    private static final Logger log = LoggerFactory.getLogger(SubmodelUtil.class);

    public static Submodel mapToInstance(String id, String idShort, String data, Submodel template) {
        Objects.requireNonNull(data, "data is null");
        Objects.requireNonNull(template, "template is null");

        Configuration conf = Configuration.defaultConfiguration();
        DocumentContext context = JsonPath.using(conf).parse(data);

        Submodel instance = deepCopy(template);

        instance.setKind(ModellingKind.INSTANCE);
        instance.setId(id);
        instance.setIdShort(composeIdShort(idShort, template));

        String basePath = "$";
        instance.getSubmodelElements().forEach(
                element -> mapSubmodelElements(element, context, "%s.%s".formatted(basePath, element.getIdShort()))
        );

        return instance;
    }

    private static void handleDataElement(DataElement dataElement, DocumentContext context, String path) {
        switch (dataElement) {
            case Property property -> readValue(context, path).ifPresent(property::setValue);
            case File file -> readValue(context, path).ifPresent(file::setValue);
            case Blob blob -> readValue(context, path)
                    .ifPresent(v -> blob.setValue(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            case Range range -> {
                readValue(context, path + ".min").ifPresent(range::setMin);
                readValue(context, path + ".max").ifPresent(range::setMax);
            }
            case MultiLanguageProperty mlp -> {
                if (mlp.getValue() != null) {
                    for (LangStringTextType text : mlp.getValue()) {
                        readValue(context, path + "." + text.getLanguage()).ifPresent(text::setText);
                    }
                }
            }
            case ReferenceElement ref -> {
                if (ref.getValue() != null && ref.getValue().getKeys() != null) {
                    for (Key key : ref.getValue().getKeys()) {
                        readValue(context, path + "." + key.getType().toString()).ifPresent(key::setValue);
                    }
                }
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported SubmodelElement type: " + dataElement.getClass().getSimpleName()
            );
        }
    }

    private static Optional<String> readValue(DocumentContext context, String path) {
        try {
            return Optional.ofNullable(context.read(path, String.class));
        } catch (PathNotFoundException e) {
            log.debug("Value for path {} not found - Element will be skipped", path);
            return Optional.empty();
        }
    }

    private static void mapSubmodelElements(SubmodelElement element, DocumentContext context, String path) {
        switch (element) {
            case DataElement dataElement -> handleDataElement(dataElement, context, path);
            case SubmodelElementCollection collection -> {
                for (SubmodelElement e : collection.getValue()){
                    mapSubmodelElements(e, context, path + "." + e.getIdShort());
                }
            }
            case SubmodelElementList list -> {
                List<SubmodelElement> elementList = list.getValue();

                for (int i = 0; i < list.getValue().size(); i++) {
                    mapSubmodelElements(elementList.get(i), context, "%s[%d]".formatted(path, i));
                }
            }
            default -> {}
        }
    }

    private static String composeIdShort(String idShort, Submodel template) {
        String templateIdShort = template.getIdShort();

        if (templateIdShort == null || templateIdShort.isEmpty()) {
            return idShort;
        }

        return templateIdShort.replace("%idShort%", idShort);
    }
}
