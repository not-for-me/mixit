/**
 * Copyright (c) 2012-2015 Edgar Espina
 * <p>
 * This file is part of Handlebars.java.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jknack.handlebars.springreactive;

import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.web.reactive.result.view.AbstractUrlBasedView;
import org.springframework.web.reactive.result.view.UrlBasedViewResolver;

import com.github.jknack.handlebars.Decorator;
import com.github.jknack.handlebars.Formatter;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HelperRegistry;
import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import com.github.jknack.handlebars.cache.NullTemplateCache;
import com.github.jknack.handlebars.cache.TemplateCache;
import com.github.jknack.handlebars.helper.DefaultHelperRegistry;
import com.github.jknack.handlebars.helper.I18nHelper;
import com.github.jknack.handlebars.helper.I18nSource;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.io.URLTemplateLoader;

/**
 * A Handlebars {@link org.springframework.web.reactive.result.view.ViewResolver view resolver}.
 *
 * @author sdeleuze
 * @author edgar.espina
 */
public class HandlebarsViewResolver extends UrlBasedViewResolver implements HelperRegistry {

    private static final Log logger = LogFactory.getLog(HandlebarsViewResolver.class);

    /**
     * The handlebars object.
     */
    private Handlebars handlebars;

    /**
     * The value's resolvers.
     */
    private ValueResolver[] valueResolvers = ValueResolver.VALUE_RESOLVERS;

    /**
     * Fail on missing file. Default is: true.
     */
    private boolean failOnMissingFile = true;

    /**
     * The helper registry.
     */
    private HelperRegistry registry = new DefaultHelperRegistry();

    /** True, if the message helper (based on {@link MessageSource}) should be registered. */
    private boolean registerMessageHelper = true;

    /**
     * If true, the i18n helpers will use a {@link MessageSource} instead of a plain
     * {@link ResourceBundle} .
     */
    private boolean bindI18nToMessageSource;

    /**
     * If true, templates will be deleted once applied. Useful, in some advanced template inheritance
     * use cases. Used by <code>{{#block}} helper</code>. Default is: false.
     * At any time you can override the default setup with:
     *
     * <pre>
     * {{#block "footer" delete-after-merge=true}}
     * </pre>
     *
     */
    private boolean deletePartialAfterMerge;

    /**
     * Set variable formatters.
     */
    private Formatter[] formatters;

    /** Location of the handlebars.js file. */
    private String handlebarsJsFile;

    /** Template cache. */
    private TemplateCache templateCache = new HighConcurrencyTemplateCache();

    /**
     * Creates a new {@link HandlebarsViewResolver}.
     *
     * @param viewClass The view's class. Required.
     */
    public HandlebarsViewResolver(
            final Class<? extends HandlebarsView> viewClass) {
        setViewClass(viewClass);
        setPrefix(TemplateLoader.DEFAULT_PREFIX);
        setSuffix(TemplateLoader.DEFAULT_SUFFIX);
    }

    /**
     * Creates a new {@link HandlebarsViewResolver}.
     */
    public HandlebarsViewResolver() {
        this(HandlebarsView.class);
    }

    /**
     * Creates a new {@link HandlebarsViewResolver} that utilizes the parameter handlebars for the
     * underlying template lifecycle management.
     *
     * @param handlebars The {@link Handlebars} instance used for template lifecycle management.
     *                   Required.
     */
    public HandlebarsViewResolver(final Handlebars handlebars) {
        this(handlebars, HandlebarsView.class);
    }

    /**
     * Creates a new {@link HandlebarsViewResolver} that utilizes the parameter handlebars for the
     * underlying template lifecycle management.
     *
     * @param handlebars The {@link Handlebars} instance used for template lifecycle management.
     *                   Required.
     * @param viewClass The view's class. Required.
     */
    public HandlebarsViewResolver(final Handlebars handlebars,
                                  final Class<? extends HandlebarsView> viewClass) {
        this(viewClass);

        this.handlebars = handlebars;
    }

    @Override
    protected AbstractUrlBasedView createUrlBasedView(String viewName) {
        return configure((HandlebarsView)super.createUrlBasedView(viewName));
    }

    /**
     * Configure the handlebars view.
     *
     * @param view The handlebars view.
     * @return The configured view.
     * @throws IOException If a resource cannot be loaded.
     */
    protected AbstractUrlBasedView configure(final HandlebarsView view) {
        String url = view.getUrl();
        // Remove prefix & suffix.
         url = url.substring(getPrefix().length(), url.length()
                - getSuffix().length());
        // Compile the template.
        try {
            view.setTemplate(handlebars.compile(url));
            view.setValueResolver(valueResolvers);
        } catch (IOException ex) {
            if (failOnMissingFile) {
                throw new IllegalStateException(ex);
            }
            logger.debug("File not found: " + url);
        }
        return view;
    }

    /**
     * The required view class.
     *
     * @return The required view class.
     */
    @Override
    protected Class<?> requiredViewClass() {
        return HandlebarsView.class;
    }

    @Override
    public void afterPropertiesSet() {
        // If no handlebars object was passed in as a constructor parameter
        if (handlebars == null) {
            // Creates a new template loader.
            TemplateLoader templateLoader = createTemplateLoader(getApplicationContext());

            // Creates a new handlebars object.
            handlebars = notNull(createHandlebars(templateLoader),
                    "A handlebars object is required.");
        }

        handlebars.with(registry);

        if (handlebarsJsFile != null) {
            handlebars.handlebarsJsFile(handlebarsJsFile);
        }

        if (formatters != null) {
            for (Formatter formatter : formatters) {
                handlebars.with(formatter);
            }
        }

        if (registerMessageHelper) {
            // Add a message source helper
            handlebars.registerHelper("message", new MessageSourceHelper(getApplicationContext()));
        }

        Collection<HelperSource> helperSources = getApplicationContext().getBeansOfType(HelperSource.class).values();
        for (HelperSource helperSource : helperSources) {
            handlebars.registerHelper(helperSource.getName(), helperSource.getHelper());
        }

        if (bindI18nToMessageSource) {
            I18nSource i18nSource = createI18nSource(getApplicationContext());

            I18nHelper.i18n.setSource(i18nSource);
            I18nHelper.i18nJs.setSource(i18nSource);
        }

        TemplateCache cache = handlebars.getCache();
        if (cache == NullTemplateCache.INSTANCE) {
            handlebars.with(templateCache);
        }

        // set delete partial after merge
        handlebars.setDeletePartialAfterMerge(deletePartialAfterMerge);
    }

    /**
     * Creates a new i18n source.
     *
     * @param context The application context.
     * @return A new i18n source.
     */
    private static I18nSource createI18nSource(final ApplicationContext context) {
        return new I18nSource() {
            @Override
            public String message(final String key, final Locale locale, final Object... args) {
                return context.getMessage(key, args, locale);
            }

            @Override
            public String[] keys(final String basename, final Locale locale) {
                ResourceBundle bundle = ResourceBundle.getBundle(basename, locale);
                Enumeration<String> keys = bundle.getKeys();
                List<String> result = new ArrayList<String>();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    result.add(key);
                }
                return result.toArray(new String[result.size()]);
            }
        };
    }

    /**
     * Creates a new {@link Handlebars} object using the parameter {@link TemplateLoader}.
     *
     * @param templateLoader A template loader.
     * @return A new handlebar's object.
     */
    protected Handlebars createHandlebars(final TemplateLoader templateLoader) {
        return new Handlebars(templateLoader);
    }

    /**
     * Creates a new template loader.
     *
     * @param context The application's context.
     * @return A new template loader.
     */
    protected TemplateLoader createTemplateLoader(final ApplicationContext context) {
        URLTemplateLoader templateLoader = new SpringTemplateLoader(context);
        // Override prefix and suffix.
        templateLoader.setPrefix(getPrefix());
        templateLoader.setSuffix(getSuffix());
        return templateLoader;
    }

    /**
     * A handlebars instance.
     *
     * @return A handlebars instance.
     */
    public Handlebars getHandlebars() {
        if (handlebars == null) {
            throw new IllegalStateException(
                    "afterPropertiesSet() method hasn't been call it.");
        }
        return handlebars;
    }

    /**
     * Set the value resolvers.
     *
     * @param valueResolvers The value resolvers. Required.
     */
    public void setValueResolvers(final ValueResolver... valueResolvers) {
        this.valueResolvers = notEmpty(valueResolvers,
                "At least one value-resolver must be present.");
    }

    /**
     * Set variable formatters.
     *
     * @param formatters Formatters to add.
     */
    public void setFormatters(final Formatter... formatters) {
        this.formatters = notEmpty(formatters,
                "At least one formatter must be present.");
    }

    /**
     * Set the handlebars.js location used it to compile/precompile template to JavaScript.
     * <p>
     * Using handlebars.js 2.x:
     * </p>
     *
     * <pre>
     *   Handlebars handlebars = new Handlebars()
     *      .handlebarsJsFile("handlebars-v2.0.0.js");
     * </pre>
     * <p>
     * Using handlebars.js 1.x:
     * </p>
     *
     * <pre>
     *   Handlebars handlebars = new Handlebars()
     *      .handlebarsJsFile("handlebars-v4.0.4.js");
     * </pre>
     *
     * Default handlebars.js is <code>handlebars-v4.0.4.js</code>.
     *
     * @param location A classpath location of the handlebar.js file.
     */
    public void setHandlebarsJsFile(final String location) {
        this.handlebarsJsFile = notEmpty(location, "Location is required");
    }

    /**
     * True, if the view resolver should fail on missing files. Default is: true.
     *
     * @param failOnMissingFile True, if the view resolver should fail on
     *        missing files. Default is: true.
     */
    public void setFailOnMissingFile(final boolean failOnMissingFile) {
        this.failOnMissingFile = failOnMissingFile;
    }

    /**
     * Register all the helpers in the map.
     *
     * @param helpers The helpers to be registered. Required.
     * @see Handlebars#registerHelper(String, Helper)
     */
    public void setHelpers(final Map<String, Helper<?>> helpers) {
        notNull(helpers, "The helpers are required.");
        for (Entry<String, Helper<?>> helper : helpers.entrySet()) {
            registry.registerHelper(helper.getKey(), helper.getValue());
        }
    }

    /**
     * Register all the helpers in the list. Each element of the list must be a helper source.
     *
     * @param helpers The helpers to be registered. Required.
     * @see Handlebars#registerHelpers(Class)
     * @see Handlebars#registerHelpers(Object)
     */
    public void setHelperSources(final List<?> helpers) {
        notNull(helpers, "The helpers are required.");
        for (Object helper : helpers) {
            registry.registerHelpers(helper);
        }
    }

    /**
     * <p>
     * Register all the helper methods for the given helper source.
     * </p>
     * <p>
     * A helper method looks like:
     * </p>
     *
     * <pre>
     * public static? CharSequence methodName(context?, parameter*, options?) {
     * }
     * </pre>
     *
     * Where:
     * <ul>
     * <li>A method can/can't be static</li>
     * <li>The method's name became the helper's name</li>
     * <li>Context, parameters and options are all optionals</li>
     * <li>If context and options are present they must be the first and last arguments of the
     *    method</li>
     * </ul>
     *
     * Instance and static methods will be registered as helpers.
     *
     * @param helperSource The helper source. Required.
     * @return This handlebars object.
     */
    @Override
    public HandlebarsViewResolver registerHelpers(final Object helperSource) {
        registry.registerHelpers(helperSource);
        return this;
    }

    /**
     * <p>
     * Register all the helper methods for the given helper source.
     * </p>
     * <p>
     * A helper method looks like:
     * </p>
     *
     * <pre>
     * public static? CharSequence methodName(context?, parameter*, options?) {
     * }
     * </pre>
     *
     * Where:
     * <ul>
     * <li>A method can/can't be static</li>
     * <li>The method's name became the helper's name</li>
     * <li>Context, parameters and options are all optionals</li>
     * <li>If context and options are present they must be the first and last arguments of the
     *    method</li>
     * </ul>
     *
     * Only static methods will be registered as helpers.
     *
     * @param helperSource The helper source. Required.
     * @return This handlebars object.
     */
    @Override
    public HandlebarsViewResolver registerHelpers(final Class<?> helperSource) {
        registry.registerHelpers(helperSource);
        return this;
    }

    @Override
    public <C> Helper<C> helper(final String name) {
        return registry.helper(name);
    }

    @Override
    public Set<Entry<String, Helper<?>>> helpers() {
        return registry.helpers();
    }

    @Override
    public <H> HandlebarsViewResolver registerHelper(final String name, final Helper<H> helper) {
        registry.registerHelper(name, helper);
        return this;
    }

    @Override
    public <H> HandlebarsViewResolver registerHelperMissing(final Helper<H> helper) {
        registry.registerHelperMissing(helper);
        return this;
    }

    @Override
    public HandlebarsViewResolver registerHelpers(final URI location) throws Exception {
        registry.registerHelpers(location);
        return this;
    }

    @Override
    public HandlebarsViewResolver registerHelpers(final File input) throws Exception {
        registry.registerHelpers(input);
        return this;
    }

    @Override
    public HandlebarsViewResolver registerHelpers(final String filename, final Reader source)
            throws Exception {
        registry.registerHelpers(filename, source);
        return this;
    }

    @Override
    public HandlebarsViewResolver registerHelpers(final String filename, final InputStream source)
            throws Exception {
        registry.registerHelpers(filename, source);
        return this;
    }

    @Override
    public HandlebarsViewResolver registerHelpers(final String filename, final String source)
            throws Exception {
        registry.registerHelpers(filename, source);
        return this;
    }

    /**
     * Same as {@link #setRegisterMessageHelper(boolean)} with a false argument. The message helper
     * wont be registered when you call this method.
     *
     * @return This handlebars view resolver.
     */
    public HandlebarsViewResolver withoutMessageHelper() {
        setRegisterMessageHelper(false);
        return this;
    }

    /**
     * True, if the message helper (based on {@link MessageSource}) should be registered. Default is:
     * true.
     *
     * @param registerMessageHelper True, if the message helper (based on {@link MessageSource})
     *        should be registered. Default is: true.
     */
    public void setRegisterMessageHelper(final boolean registerMessageHelper) {
        this.registerMessageHelper = registerMessageHelper;
    }

    /**
     * @param bindI18nToMessageSource If true, the i18n helpers will use a {@link MessageSource}
     *        instead of a plain {@link ResourceBundle}. Default is: false.
     */
    public void setBindI18nToMessageSource(final boolean bindI18nToMessageSource) {
        this.bindI18nToMessageSource = bindI18nToMessageSource;
    }

    /**
     * If true, templates will be deleted once applied. Useful, in some advanced template inheritance
     * use cases. Used by <code>{{#block}} helper</code>. Default is: false.
     * At any time you can override the default setup with:
     *
     * <pre>
     * {{#block "footer" delete-after-merge=true}}
     * </pre>
     *
     * @param deletePartialAfterMerge True for clearing up templates once they got applied. Used by
     *        <code>{{#block}} helper</code>.
     */
    public void setDeletePartialAfterMerge(final boolean deletePartialAfterMerge) {
        this.deletePartialAfterMerge = deletePartialAfterMerge;
    }

    /**
     * @param templateCache Set a template cache. Default is: {@link HighConcurrencyTemplateCache}.
     */
    public void setTemplateCache(final TemplateCache templateCache) {
        this.templateCache = templateCache;
    }

    @Override
    public Decorator decorator(final String name) {
        return this.registry.decorator(name);
    }

    @Override
    public HandlebarsViewResolver registerDecorator(final String name, final Decorator decorator) {
        registry.registerDecorator(name, decorator);
        return this;
    }
}