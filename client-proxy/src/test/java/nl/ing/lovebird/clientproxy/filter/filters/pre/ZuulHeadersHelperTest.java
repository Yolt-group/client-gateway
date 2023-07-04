package nl.ing.lovebird.clientproxy.filter.filters.pre;

import com.netflix.zuul.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static nl.ing.lovebird.clientproxy.filter.filters.pre.ZuulHeadersHelper.IGNORED_HEADERS_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZuulHeadersHelperTest {

    static final String DEFAULT_HEADER_NAME = "name";
    static final String DEFAULT_HEADER_VALUE = "value";

    ZuulHeadersHelper zuulHeadersHelper;

    @Mock
    RequestContext contextMock;

    @BeforeEach
    void setUp() {
        zuulHeadersHelper = new ZuulHeadersHelper();
    }

    @Test
    void whenNoIgnoredHeaders_shouldNotCreateThem() {
        when(contextMock.get(ArgumentMatchers.eq(IGNORED_HEADERS_SET))).thenReturn(null);

        zuulHeadersHelper.setAndEnable(contextMock, DEFAULT_HEADER_NAME, DEFAULT_HEADER_VALUE);

        assertThat(contextMock.get(IGNORED_HEADERS_SET)).isNull();
    }

    @Test
    void whenEmptyIgnoredHeaders_shouldNotCreateThem() {
        when(contextMock.get(ArgumentMatchers.eq(IGNORED_HEADERS_SET))).thenReturn(Collections.emptySet());

        zuulHeadersHelper.setAndEnable(contextMock, DEFAULT_HEADER_NAME, DEFAULT_HEADER_VALUE);

        assertThat((Set) contextMock.get(IGNORED_HEADERS_SET)).isEmpty();
    }

    @Test
    void whenHeaderIsNotIgnored_shouldNotIgnoreIt() {
        when(contextMock.get(ArgumentMatchers.eq(IGNORED_HEADERS_SET))).thenReturn(Collections.singleton("different name"));

        zuulHeadersHelper.setAndEnable(contextMock, DEFAULT_HEADER_NAME, DEFAULT_HEADER_VALUE);

        assertThat((Set<String>) contextMock.get(IGNORED_HEADERS_SET)).containsExactly("different name");
    }

    @Test
    void whenHeaderIsIgnored_shouldBeEnabled() {
        Set<String> ignoredHeaders = new HashSet<>();
        ignoredHeaders.add(DEFAULT_HEADER_NAME);
        when(contextMock.get(ArgumentMatchers.eq(IGNORED_HEADERS_SET))).thenReturn(ignoredHeaders);

        zuulHeadersHelper.setAndEnable(contextMock, DEFAULT_HEADER_NAME, DEFAULT_HEADER_VALUE);

        assertThat((Set<String>) contextMock.get(IGNORED_HEADERS_SET)).doesNotContain(DEFAULT_HEADER_NAME);
    }
}
