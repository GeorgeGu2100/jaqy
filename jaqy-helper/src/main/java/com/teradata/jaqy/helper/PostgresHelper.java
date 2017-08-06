/*
 * Copyright (c) 2017 Teradata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.jaqy.helper;

import java.sql.SQLException;

import com.teradata.jaqy.Globals;
import com.teradata.jaqy.connection.JaqyConnection;
import com.teradata.jaqy.connection.JaqyResultSetMetaData;
import com.teradata.jaqy.connection.JdbcFeatures;

/**
 * @author	Heng Yuan
 */
class PostgresHelper extends DefaultHelper
{
	public PostgresHelper (JaqyConnection conn, Globals globals)
	{
		super (new JdbcFeatures (), conn, globals);
	}

	@Override
	public boolean isSpatialColumn (JaqyResultSetMetaData meta, int column) throws SQLException
	{
		/* PostGIS uses Geosgraphy instead of ST_GEOMETRY which is in the
		 * SQL-MM standard.
		 */
		if ("geography".equalsIgnoreCase (meta.getColumnTypeName (column)))
			return true;
		return false;
	}
}
