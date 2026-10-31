package com.skelar.defuse.spi;
import com.skelar.defuse.model.domain.Ticket;
import java.util.List;
/** Read-only input boundary. Customer replies belong to the trusted pipeline, not a source plugin. */
public interface TicketSource { List<Ticket> open(); }
