package matchingengine.matchingengine.adapter.in.rest;

import matchingengine.matchingengine.application.port.in.CancelOrderUseCase;
import matchingengine.matchingengine.application.port.in.GetOrderBookUseCase;
import matchingengine.matchingengine.application.port.in.SubmitOrderUseCase;
import matchingengine.matchingengine.domain.*;
import matchingengine.matchingengine.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {

    private MockMvc mockMvc;
    private SubmitOrderUseCase submitOrder;
    private CancelOrderUseCase cancelOrder;
    private GetOrderBookUseCase getOrderBook;

    @BeforeEach
    void setUp() {
        submitOrder  = mock(SubmitOrderUseCase.class);
        cancelOrder  = mock(CancelOrderUseCase.class);
        getOrderBook = mock(GetOrderBookUseCase.class);

        OrderController controller = new OrderController(submitOrder, cancelOrder, getOrderBook);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- POST /api/orders ---

    @Test
    void submitOrder_noMatch_returns200WithEmptyTrades() throws Exception {
        when(submitOrder.submit(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrument": "BTC-USD",
                                  "side": "BUY",
                                  "price": "100",
                                  "quantity": "5"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    void submitOrder_withTrade_returns200WithTradeDetails() throws Exception {
        Trade trade = new Trade("BTC-USD", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100"), new BigDecimal("5"));
        when(submitOrder.submit(any())).thenReturn(List.of(trade));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrument": "BTC-USD",
                                  "side": "BUY",
                                  "price": "100",
                                  "quantity": "5"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades[0].price").value(100))
                .andExpect(jsonPath("$.trades[0].quantity").value(5));
    }

    @Test
    void submitOrder_invalidPrice_returns400() throws Exception {
        when(submitOrder.submit(any()))
                .thenThrow(new IllegalArgumentException("Price must be positive"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrument": "BTC-USD",
                                  "side": "BUY",
                                  "price": "-1",
                                  "quantity": "5"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE /api/orders/{id} ---

    @Test
    void cancelOrder_existingOrder_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("5"));
        when(cancelOrder.cancel(id)).thenReturn(order);

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelOrder_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancelOrder.cancel(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/orderbook/{instrument} ---

    @Test
    void getOrderBook_existingInstrument_returns200() throws Exception {
        OrderBook book = new OrderBook("BTC-USD");
        book.addOrder(new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("3")));
        when(getOrderBook.getOrderBook("BTC-USD")).thenReturn(Optional.of(book));

        mockMvc.perform(get("/api/orderbook/BTC-USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrument").value("BTC-USD"))
                .andExpect(jsonPath("$.bids[0].price").value(100));
    }

    @Test
    void getOrderBook_unknownInstrument_returns404() throws Exception {
        when(getOrderBook.getOrderBook("ETH-USD")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orderbook/ETH-USD"))
                .andExpect(status().isNotFound());
    }
}
