package com.doof.passwordmanager.app;

import com.doof.passwordmanager.dao.JdbcAccountDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.service.ApplicationConnector;

public final class AppBootstrap {

    private AppBootstrap() {}

    public static ApplicationConnector createProductionConnector() {
        JdbcVaultMetadataDao vmDao = new JdbcVaultMetadataDao();
        JdbcAccountDao accountDao = new JdbcAccountDao();
        return new ApplicationConnector(vmDao, accountDao);
    }
}
