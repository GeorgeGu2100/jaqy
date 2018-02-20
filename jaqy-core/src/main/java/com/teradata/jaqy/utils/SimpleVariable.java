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
package com.teradata.jaqy.utils;

import com.teradata.jaqy.interfaces.Variable;

/**
 * @author	Heng Yuan
 */
public class SimpleVariable implements Variable
{
	private String m_name;
	private Object m_obj;
	private String m_description = "User defined variable";

	public SimpleVariable (String name)
	{
		m_name = name;
	}

	@Override
	public Object get ()
	{
		return m_obj;
	}

	@Override
	public boolean set (Object value)
	{
		m_obj = value;
		return true;
	}

	public String getName ()
	{
		return m_name;
	}

	@Override
	public String getDescription ()
	{
		return m_description;
	}

	public void setDescription (String description)
	{
		m_description = description;
	}
}
