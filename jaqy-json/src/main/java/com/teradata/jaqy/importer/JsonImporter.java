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
package com.teradata.jaqy.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.Types;
import java.util.HashMap;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonParser.Event;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.yuanheng.cookjson.CookJsonParser;
import org.yuanheng.cookjson.CookJsonProvider;
import org.yuanheng.cookjson.value.CookJsonBinary;

import com.teradata.jaqy.interfaces.Display;
import com.teradata.jaqy.interfaces.JaqyImporter;
import com.teradata.jaqy.utils.JsonBinaryFormat;
import com.teradata.jaqy.utils.JsonFormat;
import com.teradata.jaqy.utils.JsonUtils;

/**
 * @author	Heng Yuan
 */
class JsonImporter implements JaqyImporter<String>
{
	private final CookJsonParser m_parser;
	private JsonObject m_node;
	private boolean m_rootIsArray;
	private boolean m_end;
	private final JsonBinaryFormat m_binaryFormat;

	public JsonImporter (InputStream is, Charset charset, JsonFormat format, JsonBinaryFormat binaryFormat, boolean rootAsArray) throws IOException
	{
		m_binaryFormat = binaryFormat;

		JsonProvider provider = new CookJsonProvider ();

		HashMap<String, Object> config = new HashMap<String, Object> ();
		switch (format)
		{
			case Text:
			{
				config.put (CookJsonProvider.FORMAT, CookJsonProvider.FORMAT_JSON);
				break;
			}
			case Bson:
				config.put (CookJsonProvider.FORMAT, CookJsonProvider.FORMAT_BSON);
				if (rootAsArray)
					config.put (CookJsonProvider.ROOT_AS_ARRAY, Boolean.TRUE);
				break;
		}

		CookJsonParser p = (CookJsonParser) provider.createParserFactory (config).createParser (is, charset);
		m_parser = p;

		// now read the first token
		Event e = p.next ();
		if (e == Event.START_ARRAY)
			m_rootIsArray = true;
		else if (e == Event.START_OBJECT)
			m_rootIsArray = false;
		else
			throw new IOException ("invalid json file format.");
	}

	private JsonObject readJsonObject () throws IOException
	{
		if (m_rootIsArray)
		{
			Event e = m_parser.next ();
			if (e == Event.END_ARRAY ||
				e == Event.END_OBJECT)
			{
				m_end = true;
				return null;
			}
		}
		else
		{
			m_end = true;
		}

		JsonValue v = m_parser.getValue ();

		if (!(v instanceof JsonObject))
		{
			throw new IOException ("Invalid JSON format for database import.");
		}
		return (JsonObject)v;
	}

	@Override
	public void close () throws IOException
	{
		m_parser.close ();
	}

	@Override
	public String getName ()
	{
		return "json";
	}

	@Override
	public void showSchema (Display display)
	{
	}

	@Override
	public boolean next ()
	{
		if (m_end)
			return false;
		try
		{
			m_node = readJsonObject ();
			if (m_node != null)
				return true;
		}
		catch (Exception ex)
		{
		}
		m_end = true;
		m_parser.close ();
		return false;
	}

	@Override
	public Object getObject (int index, int type) throws IOException
	{
		throw new IOException ("json data has to be accessed via field.");
	}

	@Override
	public String getPath (String name) throws Exception
	{
		return name;
	}

	@Override
	public Object getObjectFromPath (String name, int type) throws Exception
	{
		JsonValue v = m_node.get (name);
		if (v.getValueType () == ValueType.NULL) 
			return null;
		switch (type)
		{
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			{
				if (v.getValueType () == ValueType.TRUE)
					return 1;
				else if (v.getValueType () == ValueType.FALSE)
					return 0;

				if (v instanceof JsonString)
				{
					return Integer.parseInt (((JsonString)v).getString ());
				}
				else if (v instanceof JsonNumber)
				{
					return ((JsonNumber)v).intValue ();
				}
				throw new IOException ("Invalid type.");
			}
			case Types.FLOAT:
			{
				if (v instanceof JsonString)
				{
					return Float.parseFloat (((JsonString)v).getString ());
				}
				else if (v instanceof JsonNumber)
				{
					return ((JsonNumber)v).doubleValue ();
				}
				throw new IOException ("Invalid type.");
			}
			case Types.DOUBLE:
			{
				if (v instanceof JsonString)
				{
					return Double.parseDouble (((JsonString)v).getString ());
				}
				else if (v instanceof JsonNumber)
				{
					return ((JsonNumber)v).doubleValue ();
				}
				throw new IOException ("Invalid type.");
			}
			case Types.DECIMAL:
			{
				if (v instanceof JsonString)
				{
					return new BigDecimal (((JsonString)v).getString ());
				}
				else if (v instanceof JsonNumber)
				{
					return ((JsonNumber)v).bigDecimalValue ();
				}
				throw new IOException ("Invalid type.");
			}
			case Types.DATE:
			{
				if (v instanceof JsonString)
				{
					return Date.valueOf (((JsonString)v).getString ());
				}
				throw new IOException ("Invalid type.");
			}
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.NCHAR:
			case Types.NVARCHAR:
			case Types.LONGNVARCHAR:
			case Types.LONGVARCHAR:
			case Types.CLOB:
			case Types.NCLOB:
			{
				if (v instanceof JsonString)
				{
					return ((JsonString)v).getString ();
				}
				else if (v instanceof JsonStructure)
				{
					return JsonUtils.toString ((JsonValue)v);
				}
				return v.toString ();
			}
			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:
			case Types.BLOB:
			{
				if (v instanceof CookJsonBinary)
				{
					return ((CookJsonBinary)v).getBytes ();
				}
				if (v instanceof JsonString)
				{
					String str = ((JsonString)v).getString ();
					switch (m_binaryFormat)
					{
						case Base64:
							return Base64.decodeBase64 (str);
						case Hex:
							return Hex.decodeHex (str.toCharArray ());
					}
				}
				throw new IOException ("Invalid type.");
			}
			case Types.OTHER:
			{
				if (v instanceof JsonString)
				{
					return ((JsonString)v).getString ();
				}
				if (v instanceof CookJsonBinary)
				{
					return ((CookJsonBinary)v).getBytes ();
				}
				if (v instanceof JsonNumber)
				{
					return ((JsonNumber)v).bigDecimalValue ();
				}
				return JsonUtils.toString (v);
			}
		}
		throw new IOException ("Invalid type.");
	}
}
