package org.craftcoin.walletconnectmc;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.File;

public class DatabaseConnection {
  private final SessionFactory factory;
  private final Session session;

  public static DatabaseConnection connect(File configFile) {
    StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .configure(configFile)
        .build();
    try {
      SessionFactory factory = new MetadataSources(registry)
          .addAnnotatedClass(UuidToAddressMapping.class)
          .buildMetadata()
          .buildSessionFactory();
      return new DatabaseConnection(factory);
    } catch (Exception exception) {
      StandardServiceRegistryBuilder.destroy(registry);
      throw exception;
    }
  }

  private DatabaseConnection(SessionFactory factory) {
    this.factory = factory;
    this.session = factory.openSession();
  }

  public SessionFactory getSessionFactory() {
    return factory;
  }

  public Session getSession() {
    return session;
  }

  public void close() {
    session.close();
    factory.close();
  }
}
