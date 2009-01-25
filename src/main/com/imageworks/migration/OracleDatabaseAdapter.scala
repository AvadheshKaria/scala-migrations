package com.imageworks.migration

/**
 * Map the BIGINT SQL type to a NUMBER(19, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * BIGINT as a 8-byte signed integer type.  On Oracle a NUMBER(19, 0)
 * is large enough to store any integers from -9223372036854775808 to
 * 9223372036854775807 but not any integers with more digits.  A
 * NUMBER(19, 0) does allow a larger range of values than the other
 * databases, from -9999999999999999999 to 9999999999999999999, but
 * this seems like an acceptable solution without using a CHECK
 * constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(19, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleBigintColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  val sql = "NUMBER(19, 0)"
}

class OracleCharColumnDefinition(use_nchar_type : Boolean)
  extends ColumnDefinition
  with ColumnSupportsDefault
  with ColumnSupportsLimit
{
  def sql = if (use_nchar_type)
              column_sql("NCHAR")
            else
              column_sql("CHAR")
}

class OracleDecimalColumnDefinition
  extends AbstractDecimalColumnDefinition
{
  val decimal_sql_name = "NUMBER"
}

/**
 * Map the INTEGER SQL type to a NUMBER(10, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * INTEGER as a 4-byte signed integer type.  On Oracle a NUMBER(10, 0)
 * is large enough to store any integers from -2147483648 to
 * 2147483647 but not any integers with more digits.  A NUMBER(10, 0)
 * does allow a larger range of values than the other databases, from
 * -9999999999 to 9999999999, but this seems like an acceptable
 * solution without using a CHECK constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(10, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleIntegerColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  val sql = "NUMBER(10, 0)"
}

/**
 * Map the SMALLINT SQL type to a NUMBER(5, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * SMALLINT as a 2-byte signed integer type.  On Oracle a NUMBER(5, 0)
 * is large enough to store any integers from -32768 to 32767 but not
 * any integers with more digits.  A NUMBER(5, 0) does allow a larger
 * range of values than the other databases, from -99999 to 99999, but
 * this seems like an acceptable solution without using a CHECK
 * constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(5, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleSmallintColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  val sql = "NUMBER(5, 0)"
}

class OracleVarbinaryColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
  with ColumnSupportsLimit
{
  def sql =
  {
    if (! limit.isDefined) {
      val message = "In Oracle, a RAW column must always specify its size."
      throw new IllegalArgumentException(message)
    }

    column_sql("RAW")
  }
}

class OracleVarcharColumnDefinition(use_nchar_type : Boolean)
  extends ColumnDefinition
  with ColumnSupportsDefault
  with ColumnSupportsLimit
{
  def sql = if (use_nchar_type)
              column_sql("NVARCHAR2")
            else
              column_sql("VARCHAR2")
}

class OracleDatabaseAdapter(override val schema_name_opt : Option[String])
  extends DatabaseAdapter(schema_name_opt)
{
  override
  def column_definition_factory(column_type : SqlType,
                                character_set_opt : Option[CharacterSet]) : ColumnDefinition =
  {
    val use_nchar_type =
      character_set_opt match {
        case None => {
          false
        }
        case Some(CharacterSet(Unicode)) => {
          true
        }
        case Some(set @ CharacterSet(name)) => {
          logger.warn("Ignoring '{}' as Oracle only supports specifying no " +
                      "explicit character set encoding, which defaults the " +
                      "column to use the database's character set, or " +
                      "Unicode.",
                      set)
          false
        }
      }

    column_type match {
      case BigintType =>
        new OracleBigintColumnDefinition
      case BlobType =>
        new DefaultBlobColumnDefinition
      case BooleanType => {
        val message = "Oracle does not support a boolean type, you must " +
                      "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case CharType =>
        new OracleCharColumnDefinition(use_nchar_type)
      case DecimalType =>
        new OracleDecimalColumnDefinition
      case IntegerType =>
        new OracleIntegerColumnDefinition
      case SmallintType =>
        new OracleSmallintColumnDefinition
      case TimestampType =>
        new DefaultTimestampColumnDefinition
      case VarbinaryType =>
        new OracleVarbinaryColumnDefinition
      case VarcharType =>
        new OracleVarcharColumnDefinition(use_nchar_type)
    }
  }

  override
  def remove_index_sql(schema_name_opt : Option[String],
                       table_name : String,
                       index_name : String) : String =
  {
    "DROP INDEX " +
    quote_column_name(index_name) +
    " ON " +
    quote_table_name(schema_name_opt, table_name)
  }

  override
  def grant_sql(schema_name_opt : Option[String],
                table_name : String,
                grantees : Array[String],
                privileges : GrantPrivilegeType*) : String =
  {
    // Check that no columns are defined for any SELECT privs
    for {
      SelectPrivilege(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support granting select to " +
                    "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.grant_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }

  override
  def revoke_sql(schema_name_opt : Option[String],
                 table_name : String,
                 grantees : Array[String],
                 privileges : GrantPrivilegeType*) : String =
  {
    // Check that no columns are defined for any privs with columns
    for {
      PrivilegeWithColumns(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support revoking permissions from " +
                    "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.revoke_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }

  /**
   * Return the SQL text for the ON DELETE clause for a foreign key
   * relationship.
   *
   * Oracle rejects adding a foreign key relationship containing the
   * "ON DELETE RESTRICT" text, so do not generate any SQL text for
   * it.  The behavior is the same though.  Let any other unsupported
   * options pass through, such as "ON DELETE NO ACTION", in case
   * Oracle ever does support that clause, which it does not in 10g.
   *
   * @param on_delete_opt an Option[OnDelete]
   * @param the SQL text to append to the SQL to create a foreign key
   *        relationship
   */
  override
  def on_delete_sql(on_delete_opt : Option[OnDelete]) : String =
  {
    on_delete_opt match {
      case Some(OnDelete(Restrict)) => ""
      case opt => super.on_delete_sql(opt)
    }
  }
}
