package nl.ing.lovebird.clientproxy.filter.filters;

import lombok.Getter;

@Getter
public enum FilterType {
    PRE("pre"),
    POST("post"),
    ROUTE("route"),
    ERROR("error");

    private final String type;

    FilterType(String type) {
        this.type = type;
    }
}
