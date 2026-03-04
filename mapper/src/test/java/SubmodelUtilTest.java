import dev.jackutil.mapper.SubmodelUtil;
import org.eclipse.digitaltwin.aas4j.v3.model.*;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmodelUtilTest {

    @Test
    @DisplayName("mapToInstance should throw NullPointerException on null arguments")
    void mapToInstance_NullArguments_ThrowsException() {
        Submodel template = new DefaultSubmodel.Builder().idShort("Test").build();

        assertThatThrownBy(() -> SubmodelUtil.mapToInstance("id", "idShort", null, template))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("data is null");

        assertThatThrownBy(() -> SubmodelUtil.mapToInstance("id", "idShort", "{}", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("template is null");
    }

    @Test
    @DisplayName("Should deep copy template and configure base Submodel attributes")
    void mapToInstance_BaseAttributes_MapsCorrectlyAndDeepCopies() {
        // Arrange
        Property templateProp = new DefaultProperty.Builder().idShort("Prop").value("OldValue").build();
        Submodel template = new DefaultSubmodel.Builder()
                .id("urn:old:id")
                .idShort("Template_%idShort%")
                .kind(ModellingKind.TEMPLATE)
                .submodelElements(templateProp)
                .build();

        String json = "{ \"Prop\": \"NewValue\" }";

        // Act
        Submodel instance = SubmodelUtil.mapToInstance("urn:new:id", "Instance1", json, template);

        // Assert Instance Attributes
        assertThat(instance.getKind()).isEqualTo(ModellingKind.INSTANCE);
        assertThat(instance.getId()).isEqualTo("urn:new:id");
        assertThat(instance.getIdShort()).isEqualTo("Template_Instance1");

        // Assert Deep Copy (Template should remain untouched)
        assertThat(template.getKind()).isEqualTo(ModellingKind.TEMPLATE);
        assertThat(template.getId()).isEqualTo("urn:old:id");
        assertThat(templateProp.getValue()).isEqualTo("OldValue");

        // Assert Value Mapped
        Property mappedProp = (Property) instance.getSubmodelElements().
                getFirst();
        assertThat(mappedProp.getValue()).isEqualTo("NewValue");
    }

    @Test
    @DisplayName("Should gracefully skip DataElements missing from JSON payload")
    void mapToInstance_MissingJsonPath_GracefullySkips() {
        // Arrange
        Property prop = new DefaultProperty.Builder().idShort("MissingProp").value("Default").build();
        Submodel template = new DefaultSubmodel.Builder().idShort("Test").submodelElements(prop).build();

        // JSON does not contain MissingProp
        String json = "{ \"OtherProp\": \"Value\" }";

        // Act
        Submodel instance = SubmodelUtil.mapToInstance("id", "idShort", json, template);

        // Assert
        Property mappedProp = (Property) instance.getSubmodelElements().getFirst();
        assertThat(mappedProp.getValue()).isEqualTo("Default"); // Unchanged
    }

    @Test
    @DisplayName("Should successfully map complex DataElements (Range, MLP, Reference)")
    void mapToInstance_ComplexDataElements_MapsCorrectly() {
        // Arrange
        Submodel template = new DefaultSubmodel.Builder().idShort("Test")
                .submodelElements(new DefaultRange.Builder().idShort("MyRange").min("0").max("0").build())
                .submodelElements(new DefaultMultiLanguageProperty.Builder().idShort("MyMLP")
                        .value(List.of(
                                new DefaultLangStringTextType.Builder().language("en").text("").build(),
                                new DefaultLangStringTextType.Builder().language("de").text("").build()
                        )).build())
                .submodelElements(new DefaultReferenceElement.Builder().idShort("MyRef")
                        .value(new DefaultReference.Builder()
                                .keys(List.of(new DefaultKey.Builder().type(KeyTypes.GLOBAL_REFERENCE).value("").build()))
                                .build()).build())
                .build();

        String json = """
                {
                  "MyRange": { "min": "10", "max": "99" },
                  "MyMLP": { "en": "Hello", "de": "Hallo" },
                  "MyRef": { "GLOBAL_REFERENCE": "http://example.com" }
                }
                """;

        // Act
        Submodel instance = SubmodelUtil.mapToInstance("id", "idShort", json, template);

        // Assert Range
        Range range = (Range) instance.getSubmodelElements().getFirst();
        assertThat(range.getMin()).isEqualTo("10");
        assertThat(range.getMax()).isEqualTo("99");

        // Assert MLP
        MultiLanguageProperty mlp = (MultiLanguageProperty) instance.getSubmodelElements().get(1);
        assertThat(mlp.getValue().get(0).getText()).isEqualTo("Hello");
        assertThat(mlp.getValue().get(1).getText()).isEqualTo("Hallo");

        // Assert Ref
        ReferenceElement ref = (ReferenceElement) instance.getSubmodelElements().get(2);
        assertThat(ref.getValue().getKeys().getFirst().getValue()).isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("Should accurately navigate and map deeply nested Collections and Lists")
    void mapToInstance_DeepHierarchies_MapsCorrectly() {
        // Arrange
        Submodel template = new DefaultSubmodel.Builder().idShort("Test")
                .submodelElements(new DefaultSubmodelElementCollection.Builder().idShort("MachineData")
                        .value(new DefaultSubmodelElementList.Builder().idShort("Sensors")
                                .value(List.of(
                                        new DefaultProperty.Builder().idShort("ignored_idShort_by_list").value("oldA").build(),
                                        new DefaultProperty.Builder().idShort("ignored_idShort_by_list").value("oldB").build()
                                )).build()
                        ).build()
                ).build();

        String json = """
                {
                  "MachineData": {
                    "Sensors": [
                      "newA",
                      "newB"
                    ]
                  }
                }
                """;

        // Act
        Submodel instance = SubmodelUtil.mapToInstance("id", "idShort", json, template);

        // Assert
        SubmodelElementCollection collection = (SubmodelElementCollection) instance.getSubmodelElements().getFirst();
        SubmodelElementList list = (SubmodelElementList) collection.getValue().getFirst();

        Property prop0 = (Property) list.getValue().get(0);
        Property prop1 = (Property) list.getValue().get(1);

        assertThat(prop0.getValue()).isEqualTo("newA");
        assertThat(prop1.getValue()).isEqualTo("newB");
    }
}
