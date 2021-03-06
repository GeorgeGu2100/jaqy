/*
 * Copyright (c) 2017-2018 Teradata
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
package com.teradata.jaqy.importer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import com.teradata.jaqy.JaqyInterpreter;
import com.teradata.jaqy.connection.JaqyPreparedStatement;
import com.teradata.jaqy.interfaces.JaqyImporter;
import com.teradata.jaqy.interfaces.ExpressionHandler;
import com.teradata.jaqy.schema.ParameterInfo;
import com.teradata.jaqy.schema.SchemaInfo;

/**
 * @author	Heng Yuan
 */
public class FieldImporter implements JaqyImporter<Object>, ExpressionHandler
{
	private int m_count;
	private final JaqyImporter<Object> m_importer;
	private final HashSet<String> m_fieldSet = new HashSet<String> ();
	private final HashMap<Integer, Object> m_pathMap = new HashMap<Integer, Object> ();

	@SuppressWarnings ("unchecked")
	public FieldImporter (JaqyImporter<?> importer)
	{
		m_importer = (JaqyImporter<Object>) importer;
	}

	@Override
	public void close () throws IOException
	{
		m_importer.close ();
	}

	@Override
	public String getName ()
	{
		return "field";
	}

	@Override
	public SchemaInfo getSchema () throws Exception
	{
		return m_importer.getSchema ();
	}

	@Override
	public boolean next () throws Exception
	{
		return m_importer.next ();
	}

	@Override
	public Object getObject (int index, ParameterInfo paramInfo, JaqyInterpreter interpreter) throws Exception
	{
		Object path = m_pathMap.get (new Integer (index));
		return m_importer.getObjectFromPath (path, paramInfo, interpreter);
	}

	/**
	 * Dummy since it will not be called.
	 */
	@Override
	public Object getPath (String name) throws Exception
	{
		return null;
	}

	/**
	 * Dummy since it will not be called.
	 */
	@Override
	public Object getObjectFromPath (Object path, ParameterInfo paramInfo, JaqyInterpreter interpreter) throws Exception
	{
		return null;
	}

	public boolean hasFields ()
	{
		return m_count > 0;
	}

	/**
	 * Register the field.
	 * @param	name
	 *			the field name
	 * @return	the parameter marker.
	 */
	@Override
	public Object eval (String name) throws IOException
	{
		if (!m_fieldSet.contains (name))
		{
			Object path;
			try
			{
				path = m_importer.getPath (name);
			}
			catch (Exception ex)
			{
				throw new IOException (ex.getMessage ());
			}
			if (path == null)
				throw new IOException ("field not found: " + name);
			Integer index = new Integer (m_count++);
			m_pathMap.put (index, path);
		}

		// simply return a parameter marker
		return "?";
	}

	@Override
	public void setNull (JaqyPreparedStatement stmt, int column, ParameterInfo paramInfo) throws Exception
	{
		m_importer.setNull (stmt, column, paramInfo);
	}
}
