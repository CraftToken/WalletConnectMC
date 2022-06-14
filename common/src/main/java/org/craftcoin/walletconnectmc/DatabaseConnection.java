// WalletConnectMC
// Copyright (C) 2022  CraftCoin
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.craftcoin.walletconnectmc;

import java.io.File;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

public final class DatabaseConnection {
  private final StandardServiceRegistry registry;
  private final SessionFactory factory;
  private final Session session;

  private DatabaseConnection(final StandardServiceRegistry registry, final SessionFactory factory) {
    this.registry = registry;
    this.factory = factory;
    this.session = factory.openSession();
  }

  public static DatabaseConnection connect(final File configFile) {
    final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .configure(configFile)
        .build();
    @SuppressWarnings("PMD.CloseResource")
    final SessionFactory factory = new MetadataSources(registry)
        .addAnnotatedClass(UuidToAddressMapping.class)
        .buildMetadata()
        .buildSessionFactory();
    return new DatabaseConnection(registry, factory);
  }

  public SessionFactory getSessionFactory() {
    return factory;
  }

  public Session getMainSession() {
    return session;
  }

  public Session openSession() {
    return factory.openSession();
  }

  public void close() {
    session.close();
    factory.close();
    registry.close();
  }
}
