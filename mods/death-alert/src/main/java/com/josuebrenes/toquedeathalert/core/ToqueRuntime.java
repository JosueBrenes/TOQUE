package com.josuebrenes.toquedeathalert.core;

import com.josuebrenes.toquedeathalert.hud.SidebarHud;
import com.josuebrenes.toquedeathalert.hud.TargetHealthHud;
import com.josuebrenes.toquedeathalert.migration.VanillaDeathsImporter;
import com.josuebrenes.toquedeathalert.nametag.RoleNametags;
import com.josuebrenes.toquedeathalert.series.SeriesStatsRepository;
import com.josuebrenes.toquedeathalert.series.TryWatcher;
import com.josuebrenes.toquedeathalert.tab.TabListService;
import org.jetbrains.annotations.Nullable;

/**
 * Services that only exist once a server is running.
 *
 * <p>Commands are registered while the data packs load, which happens before the
 * server object exists, so every consumer resolves its services through here at
 * call time instead of capturing them at registration time.
 */
public final class ToqueRuntime {
    @Nullable
    private volatile Services services;

    public record Services(SeriesStatsRepository stats,
                           TabListService tabList,
                           VanillaDeathsImporter importer,
                           TryWatcher tryWatcher,
                           SidebarHud hud,
                           RoleNametags nametags,
                           TargetHealthHud targetHud) {
    }

    public void bind(Services services) {
        this.services = services;
    }

    public void unbind() {
        this.services = null;
    }

    @Nullable
    public Services services() {
        return services;
    }

    @Nullable
    public SeriesStatsRepository stats() {
        Services current = services;
        return current == null ? null : current.stats();
    }

    @Nullable
    public TabListService tabList() {
        Services current = services;
        return current == null ? null : current.tabList();
    }

    @Nullable
    public VanillaDeathsImporter importer() {
        Services current = services;
        return current == null ? null : current.importer();
    }

    @Nullable
    public TryWatcher tryWatcher() {
        Services current = services;
        return current == null ? null : current.tryWatcher();
    }

    @Nullable
    public SidebarHud hud() {
        Services current = services;
        return current == null ? null : current.hud();
    }

    @Nullable
    public RoleNametags nametags() {
        Services current = services;
        return current == null ? null : current.nametags();
    }

    @Nullable
    public TargetHealthHud targetHud() {
        Services current = services;
        return current == null ? null : current.targetHud();
    }
}
