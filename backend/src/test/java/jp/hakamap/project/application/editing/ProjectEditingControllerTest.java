package jp.hakamap.project.application.editing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import jp.hakamap.project.application.history.CommandType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ProjectEditingControllerTest {
  private static final String ID = "8644022a-bca2-4c3e-b811-19c28b7a2d58";

  private final JsonMapper json = JsonMapper.builder().build();

  private final ProjectEditingController controller = new ProjectEditingController(null, json);

  @ParameterizedTest
  @MethodSource("commands")
  void convertsEveryCommandTypeToItsDedicatedPayload(
      String commandType, String payloadJson, CommandType expectedType, Class<?> expectedPayload)
      throws Exception {
    CommandType type = controller.commandType(commandType);
    CommandPayloads.CommandPayload payload =
        controller.deserializePayload(type, json.readTree(payloadJson));

    assertThat(type).isEqualTo(expectedType);
    assertThat(payload).isInstanceOf(expectedPayload);
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void rejectsUnknownCommandAndUnknownPayloadField(String commandType, String payloadJson)
      throws Exception {
    assertThatThrownBy(
            () -> {
              CommandType type = controller.commandType(commandType);
              controller.deserializePayload(type, json.readTree(payloadJson));
            })
        .isInstanceOf(EditingApiException.class);
  }

  private static Stream<Arguments> invalidCommands() {
    return Stream.of(
        Arguments.of("unknownCommand", "{}"),
        Arguments.of("renameProject", "{\"name\":\"名称\",\"unknown\":true}"));
  }

  private static Stream<Arguments> commands() {
    String rectangle = "\"x\":1,\"y\":2,\"width\":3,\"height\":4";
    String graveIds = "\"graveIds\":[\"" + ID + "\"]";
    return Stream.of(
        command(
            "renameProject",
            "{\"name\":\"名称\"}",
            CommandType.RENAME_PROJECT,
            CommandPayloads.RenameProject.class),
        command(
            "setBackground",
            "{\"fileSelectionId\":\""
                + ID
                + "\",\"x\":0,\"y\":0,\"rotation\":0,\"scaleX\":1,\"scaleY\":1}",
            CommandType.SET_BACKGROUND,
            CommandPayloads.SetBackground.class),
        command(
            "transformBackground",
            "{\"x\":0,\"y\":0,\"rotation\":0,\"scaleX\":1,\"scaleY\":1}",
            CommandType.TRANSFORM_BACKGROUND,
            CommandPayloads.TransformBackground.class),
        command(
            "removeBackground",
            "{}",
            CommandType.REMOVE_BACKGROUND,
            CommandPayloads.RemoveBackground.class),
        command(
            "createArea",
            "{\"clientRef\":\"a\",\"name\":\"A\","
                + rectangle
                + ",\"colorPreset\":\"blue\",\"visible\":true}",
            CommandType.CREATE_AREA,
            CommandPayloads.CreateArea.class),
        command(
            "updateArea",
            "{\"areaId\":\""
                + ID
                + "\",\"name\":\"A\","
                + rectangle
                + ",\"colorPreset\":\"blue\",\"visible\":true}",
            CommandType.UPDATE_AREA,
            CommandPayloads.UpdateArea.class),
        command(
            "deleteArea",
            "{\"areaId\":\"" + ID + "\"}",
            CommandType.DELETE_AREA,
            CommandPayloads.DeleteArea.class),
        command(
            "createGrave",
            "{\"clientRef\":\"g\"," + rectangle + "}",
            CommandType.CREATE_GRAVE,
            CommandPayloads.CreateGrave.class),
        command(
            "createGraveGrid",
            "{\"clientRefPrefix\":\"g\",\"x\":1,\"y\":2,\"rows\":1,\"columns\":1,"
                + "\"graveWidth\":3,\"graveHeight\":4,\"horizontalGap\":0,\"verticalGap\":0}",
            CommandType.CREATE_GRAVE_GRID,
            CommandPayloads.CreateGraveGrid.class),
        command(
            "fillGraveRange",
            "{\"clientRefPrefix\":\"g\",\"rangeX\":1,\"rangeY\":2,\"rangeWidth\":30,"
                + "\"rangeHeight\":40,\"graveWidth\":3,\"graveHeight\":4,"
                + "\"horizontalGap\":0,\"verticalGap\":0}",
            CommandType.FILL_GRAVE_RANGE,
            CommandPayloads.FillGraveRange.class),
        command(
            "updateGraveInfo",
            "{\"graveId\":\""
                + ID
                + "\",\"managementNumber\":\"1\",\"name\":\"墓所\",\"notes\":null}",
            CommandType.UPDATE_GRAVE_INFO,
            CommandPayloads.UpdateGraveInfo.class),
        command(
            "moveGraves",
            "{" + graveIds + ",\"deltaX\":1,\"deltaY\":2}",
            CommandType.MOVE_GRAVES,
            CommandPayloads.MoveGraves.class),
        command(
            "resizeGrave",
            "{\"graveId\":\"" + ID + "\"," + rectangle + "}",
            CommandType.RESIZE_GRAVE,
            CommandPayloads.ResizeGrave.class),
        command(
            "copyGraves",
            "{" + graveIds + ",\"deltaX\":1,\"deltaY\":2}",
            CommandType.COPY_GRAVES,
            CommandPayloads.CopyGraves.class),
        command(
            "deleteGraves",
            "{" + graveIds + "}",
            CommandType.DELETE_GRAVES,
            CommandPayloads.DeleteGraves.class),
        command(
            "numberGraves",
            "{\"numberingPreviewToken\":\"token\"}",
            CommandType.NUMBER_GRAVES,
            CommandPayloads.NumberGraves.class),
        command(
            "createPerson",
            "{\"graveId\":\""
                + ID
                + "\",\"clientRef\":\"p\",\"name\":\"人物\",\"posthumousName\":null}",
            CommandType.CREATE_PERSON,
            CommandPayloads.CreatePerson.class),
        command(
            "updatePerson",
            "{\"personId\":\"" + ID + "\",\"name\":\"人物\",\"posthumousName\":null}",
            CommandType.UPDATE_PERSON,
            CommandPayloads.UpdatePerson.class),
        command(
            "deletePerson",
            "{\"personId\":\"" + ID + "\"}",
            CommandType.DELETE_PERSON,
            CommandPayloads.DeletePerson.class),
        command(
            "addAttachments",
            "{\"graveId\":\"" + ID + "\",\"fileSelectionIds\":[\"" + ID + "\"]}",
            CommandType.ADD_ATTACHMENTS,
            CommandPayloads.AddAttachments.class),
        command(
            "updateAttachment",
            "{\"assetId\":\"" + ID + "\",\"displayName\":\"写真\",\"description\":null}",
            CommandType.UPDATE_ATTACHMENT,
            CommandPayloads.UpdateAttachment.class),
        command(
            "reorderAttachments",
            "{\"graveId\":\"" + ID + "\",\"orderedAssetIds\":[\"" + ID + "\"]}",
            CommandType.REORDER_ATTACHMENTS,
            CommandPayloads.ReorderAttachments.class),
        command(
            "deleteAttachment",
            "{\"assetId\":\"" + ID + "\"}",
            CommandType.DELETE_ATTACHMENT,
            CommandPayloads.DeleteAttachment.class));
  }

  private static Arguments command(
      String commandType, String payload, CommandType type, Class<?> payloadType) {
    return Arguments.of(commandType, payload, type, payloadType);
  }
}
