package com.pravoos.ai.document.internal.model.entity;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

public class PgVectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] first, float[] second) {
        return Arrays.equals(first, second);
    }

    @Override
    public int hashCode(float[] value) {
        return Arrays.hashCode(value);
    }

    @Override
    public float[] nullSafeGet(ResultSet resultSet, int position,
                               SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = resultSet.getString(position);
        if (value == null) {
            return null;
        }
        return new PGvector(value).toArray();
    }

    @Override
    public void nullSafeSet(PreparedStatement statement, float[] value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, new PGvector(value), Types.OTHER);
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
