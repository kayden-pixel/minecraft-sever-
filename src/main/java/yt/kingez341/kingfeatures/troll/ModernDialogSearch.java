package yt.kingez341.kingfeatures.troll;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;

import java.util.List;
import java.util.UUID;

public final class ModernDialogSearch {
    private final KingFeaturesPlugin plugin;
    private final TrollLabGui gui;

    public ModernDialogSearch(KingFeaturesPlugin plugin, TrollLabGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    public void open(Player player, UUID targetId, String current) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Troll Lab Search"))
                        .inputs(List.of(
                                DialogInput.text("query", Component.text("Find a troll"))
                                        .width(320)
                                        .initial(current == null ? "" : current)
                                        .maxLength(40)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Search"),
                                Component.text("Filter Troll Lab actions"),
                                120,
                                DialogAction.customClick((response, audience) -> {
                                    if (!(audience instanceof Player clicking)) return;
                                    String query = response.getText("query");
                                    Bukkit.getScheduler().runTask(plugin, () -> gui.openActions(clicking, targetId, query == null ? "" : query));
                                }, ClickCallback.Options.builder().uses(1).build())
                        ),
                        ActionButton.create(
                                Component.text("Cancel"),
                                Component.text("Return without changing the search"),
                                120,
                                null
                        )
                )));
        player.showDialog(dialog);
    }
}
