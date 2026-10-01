// [6.5.4] 하위 에이전트(code-reviewer)에게 리뷰시킬 일부러 문제 있는 코드 (컴파일 대상 아님)
public class OrderRepository {

    private static final String DB_PASSWORD = "admin1234";

    private final javax.sql.DataSource dataSource;

    public OrderRepository(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public java.sql.ResultSet findByCustomer(String name) throws Exception {
        String sql = "SELECT * FROM orders WHERE customer_name = '" + name + "'";
        return dataSource.getConnection().createStatement().executeQuery(sql);
    }
}
