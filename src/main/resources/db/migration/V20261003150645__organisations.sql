CREATE TABLE organisations (
 id UUID PRIMARY KEY, display_name VARCHAR(120) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE organisation_members (
 id UUID PRIMARY KEY, organisation_id UUID NOT NULL REFERENCES organisations(id),
 user_id UUID NOT NULL REFERENCES users(id), role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','ADMIN','MANAGER','STAFF')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REMOVED')), added_by UUID REFERENCES users(id),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_organisation_member UNIQUE (organisation_id, user_id)
);
CREATE UNIQUE INDEX uq_organisation_single_owner ON organisation_members (organisation_id) WHERE role = 'OWNER' AND status = 'ACTIVE';
CREATE INDEX idx_organisation_members_user ON organisation_members (user_id, status, organisation_id);
CREATE INDEX idx_organisation_members_list ON organisation_members (organisation_id, status, created_at, id);
CREATE TABLE organisation_invitations (
 id UUID PRIMARY KEY, organisation_id UUID NOT NULL REFERENCES organisations(id), phone_number VARCHAR(20) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','MANAGER','STAFF')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED','EXPIRED')),
 invited_by UUID NOT NULL REFERENCES users(id), responded_by UUID REFERENCES users(id),
 expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_organisation_invitation_pending ON organisation_invitations (organisation_id, phone_number) WHERE status = 'PENDING';
CREATE INDEX idx_organisation_invitations_phone ON organisation_invitations (phone_number, status, created_at, id);
CREATE INDEX idx_organisation_invitations_org ON organisation_invitations (organisation_id, status, created_at, id);
CREATE INDEX idx_organisations_admin_list ON organisations (created_at, id);
