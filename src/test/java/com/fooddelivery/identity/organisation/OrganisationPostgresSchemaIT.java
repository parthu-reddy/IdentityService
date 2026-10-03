package com.fooddelivery.identity.organisation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit integration run against an isolated PostgreSQL instance; never point at Dev. */
class OrganisationPostgresSchemaIT {
    @Test void freshSchemaIsRepeatableAndDatabaseEnforcesOwnershipInvitationsAndAppendOnlyAudit() throws Exception {
        String url=System.getProperty("bp.pg.url");
        assertNotNull(url,"Supply -Dbp.pg.url for an isolated PostgreSQL test database");
        String username=System.getProperty("bp.pg.user","bp_o1");
        String schema="bp_o1_"+UUID.randomUUID().toString().replace("-","");
        try (Connection connection=DriverManager.getConnection(url,username,""); Statement sql=connection.createStatement()) {
            try {
                var flyway=Flyway.configure().dataSource(url,username,"").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load();
                assertEquals(2,flyway.migrate().migrationsExecuted);
                assertEquals(0,flyway.migrate().migrationsExecuted);
                flyway.validate();
                sql.execute("set search_path to "+schema);
                UUID first=UUID.randomUUID(),second=UUID.randomUUID(),org=UUID.randomUUID(),audit=UUID.randomUUID();
                sql.execute("insert into users(id,phone_number) values ('"+first+"','8999000301'),('"+second+"','8999000302')");
                sql.execute("insert into organisations(id,display_name,status,created_by,created_at,updated_at) values ('"+org+"','Fresh schema test','ACTIVE','"+first+"',now(),now())");
                sql.execute("insert into organisation_members(id,organisation_id,user_id,role,status,created_at,updated_at) values ('"+UUID.randomUUID()+"','"+org+"','"+first+"','OWNER','ACTIVE',now(),now())");
                var duplicateOwner=assertThrows(SQLException.class,() -> sql.execute("insert into organisation_members(id,organisation_id,user_id,role,status,created_at,updated_at) values ('"+UUID.randomUUID()+"','"+org+"','"+second+"','OWNER','ACTIVE',now(),now())"));
                assertEquals("23505",duplicateOwner.getSQLState());assertTrue(duplicateOwner.getMessage().contains("uq_organisation_single_owner"));
                String invitation="insert into organisation_invitations(id,organisation_id,phone_number,role,status,invited_by,expires_at,created_at,updated_at) values ('%s','"+org+"','8999000302','STAFF','PENDING','"+first+"',now()+interval '7 days',now(),now())";
                sql.execute(invitation.formatted(UUID.randomUUID()));
                var duplicateInvitation=assertThrows(SQLException.class,() -> sql.execute(invitation.formatted(UUID.randomUUID())));
                assertEquals("23505",duplicateInvitation.getSQLState());assertTrue(duplicateInvitation.getMessage().contains("uq_organisation_invitation_pending"));
                sql.execute("insert into audit_events(id,occurred_at,actor_user_id,actor_kind,action,subject_type,subject_id,organisation_id,details) values ('"+audit+"',now(),'"+first+"','USER','ORGANISATION_CREATED','ORGANISATION','"+org+"','"+org+"','{}'::jsonb)");
                var update=assertThrows(SQLException.class,() -> sql.execute("update audit_events set reason='changed' where id='"+audit+"'"));
                assertTrue(update.getMessage().contains("audit_events is append-only"));
                var delete=assertThrows(SQLException.class,() -> sql.execute("delete from audit_events where id='"+audit+"'"));
                assertTrue(delete.getMessage().contains("audit_events is append-only"));
                try(var result=sql.executeQuery("select count(*) from audit_events where id='"+audit+"'")){assertTrue(result.next());assertEquals(1,result.getInt(1));}
            } finally {sql.execute("drop schema if exists "+schema+" cascade");}
        }
    }
}
