package com.doof.passwordmanager.app;

import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.JdbcVaultRotationDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.service.ApplicationConnector;

public final class AppBootstrap {

    private AppBootstrap() {}

    public static ApplicationConnector createProductionConnector(String[] args) {
        ConnectionManager cm = ConnectionManager.open(ConnectionManager.resolveVaultPath(args));
        JdbcVaultMetadataDao vmDao = new JdbcVaultMetadataDao(cm);
        JdbcEntryDao entryDao = new JdbcEntryDao(cm);
        JdbcVaultRotationDao rotationDao = new JdbcVaultRotationDao(cm);
        return new ApplicationConnector(vmDao, rotationDao, entryDao);
    }
}
