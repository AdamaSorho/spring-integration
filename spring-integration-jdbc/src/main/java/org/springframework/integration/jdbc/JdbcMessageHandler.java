/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.jdbc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.integration.Message;
import org.springframework.integration.MessageDeliveryException;
import org.springframework.integration.MessageHandlingException;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.LinkedCaseInsensitiveMap;

/**
 * A message handler that executes an SQL update. Dynamic query parameters are supported through the
 * {@link SqlParameterSourceFactory} abstraction, the default implementation of which wraps the message so that its bean
 * properties can be referred to by name in the query string E.g.
 *
 * <pre>
 * INSERT INTO FOOS (MESSAGE_ID, PAYLOAD) VALUES (:headers[id], :payload)
 * </pre>
 *
 * N.B. do not use quotes to escape the header keys. The default SQL parameter source (from Spring JDBC) can also handle
 * headers with dotted names (e.g. <code>business.id</code>)
 *
 * @author Dave Syer
 * @since 2.0
 */
public class JdbcMessageHandler extends AbstractMessageHandler {

	private final NamedParameterJdbcOperations jdbcOperations;

	private volatile String updateSql;

	private volatile SqlParameterSourceFactory sqlParameterSourceFactory = new BeanPropertySqlParameterSourceFactory();

	private volatile boolean keysGenerated;

	/**
	 * Constructor taking {@link DataSource} from which the DB Connection can be obtained and the select query to
	 * execute to retrieve new rows.
	 *
	 * @param dataSource Must not be null
	 * @param updateSql query to execute
	 */
	public JdbcMessageHandler(DataSource dataSource, String updateSql) {
		this.jdbcOperations = new NamedParameterJdbcTemplate(dataSource);
		this.updateSql = updateSql;
	}

	/**
	 * Constructor taking {@link JdbcOperations} instance to use for query execution and the select query to execute to
	 * retrieve new rows.
	 *
	 * @param jdbcOperations instance to use for query execution
	 * @param updateSql query to execute
	 */
	public JdbcMessageHandler(JdbcOperations jdbcOperations, String updateSql) {
		this.jdbcOperations = new NamedParameterJdbcTemplate(jdbcOperations);
		this.updateSql = updateSql;
	}

	/**
	 * Flag to indicate that the update query is an insert with autogenerated keys, which will be logged at debug level.
	 * @param keysGenerated the flag value to set
	 */
	public void setKeysGenerated(boolean keysGenerated) {
		this.keysGenerated = keysGenerated;
	}

	public void setUpdateSql(String updateSql) {
		this.updateSql = updateSql;
	}

	public void setSqlParameterSourceFactory(SqlParameterSourceFactory sqlParameterSourceFactory) {
		this.sqlParameterSourceFactory = sqlParameterSourceFactory;
	}

	/**
	 * Executes the update, passing the message into the {@link SqlParameterSourceFactory}.
	 */
	protected void handleMessageInternal(Message<?> message) throws MessageRejectedException, MessageHandlingException,
			MessageDeliveryException {
		List<? extends Map<String, Object>> keys = executeUpdateQuery(message, keysGenerated);
		if (logger.isDebugEnabled() && !keys.isEmpty()) {
			logger.debug("Generated keys: "+keys);
		}
	}

	protected List<? extends Map<String, Object>> executeUpdateQuery(Object obj, boolean keysGenerated) {
		SqlParameterSource updateParameterSource = new MapSqlParameterSource();
		if (this.sqlParameterSourceFactory != null) {
			updateParameterSource = this.sqlParameterSourceFactory.createParameterSource(obj);
		}
		if (keysGenerated) {
			KeyHolder keyHolder = new GeneratedKeyHolder();
			this.jdbcOperations.update(this.updateSql, updateParameterSource,
					keyHolder);
			return keyHolder.getKeyList();
		}
		else {
			int updated = this.jdbcOperations.update(this.updateSql, updateParameterSource);
			LinkedCaseInsensitiveMap<Object> map = new LinkedCaseInsensitiveMap<Object>();
			map.put("UPDATED", updated);
			return Collections.singletonList(map);
		}

	}

}
