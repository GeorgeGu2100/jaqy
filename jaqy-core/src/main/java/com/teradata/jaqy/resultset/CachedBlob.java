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
package com.teradata.jaqy.resultset;

import java.io.*;
import java.sql.Blob;
import java.sql.SQLException;

import com.teradata.jaqy.JaqyException;
import com.teradata.jaqy.utils.FileUtils;

/**
 * @author	Heng Yuan
 */
public class CachedBlob extends BlobWrapper implements Comparable<CachedBlob>
{
	private long m_length;
	private File m_file;
	private byte[] m_bytes;

	public CachedBlob (Blob blob, int cacheSize, byte[] byteBuffer) throws SQLException
	{
		try
		{
			m_length = blob.length ();
			if (m_length < cacheSize)
			{
				m_bytes = blob.getBytes (1, (int)m_length);
				m_file = null;
			}
			else
			{
				m_bytes = blob.getBytes (1, cacheSize);
				if (m_length > cacheSize)
				{
					m_file = FileUtils.createTempFile ();
					FileUtils.writeFile (m_file, blob.getBinaryStream (), byteBuffer);
				}
			}
			blob.free ();
		}
		catch (IOException ex)
		{
			throw new JaqyException (ex);
		}
	}

	@Override
	public long length ()
	{
		return m_length;
	}

	@Override
	public byte[] getBytes (long pos, int length) throws SQLException
	{
		// most common case check
		if (pos == 1 && length == m_bytes.length)
			return m_bytes;

		if (pos < 1 ||
			length < 0 ||
			(pos + length - 1) > m_length)
			throw new SQLException ("Invalid arguments");
		try
		{
			// if the data is already in the memory
			if ((pos + length - 1) <= m_bytes.length)
			{
				byte[] bytes = new byte[length];
				System.arraycopy (m_bytes, (int)(pos - 1), bytes, 0, length);
				return bytes;
			}
			return FileUtils.readFile (m_file, pos - 1, length);
		}
		catch (IOException ex)
		{
			throw new JaqyException (ex);
		}
	}

	@Override
	public InputStream getBinaryStream ()
	{
		try
		{
			if (m_file != null)
				return new FileInputStream (m_file);
			return new ByteArrayInputStream (m_bytes);
		}
		catch (IOException ex)
		{
			throw new JaqyException (ex);
		}
	}

	@Override
	public void free ()
	{
		if (m_bytes != null)
		{
			m_bytes = null;
			if (m_file != null)
			{
				m_file.delete ();
				m_file = null;
			}
		}
	}

	@Override
	public int compareTo (CachedBlob o)
	{
		try
		{
			return FileUtils.compare (getBinaryStream(), o.getBinaryStream ());
		}
		catch (Exception ex)
		{
			// shouldn't reach here
			return -1;
		}
	}
}