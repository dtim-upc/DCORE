#!/usr/bin/env stack
-- stack --resolver lts-18.13 script --package turtle --package foldl --package text
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE DerivingStrategies #-}
{-# LANGUAGE ViewPatterns #-}
{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE RecordWildCards #-}
{-# LANGUAGE GADTs #-}

-----------------------------------------

import Turtle
import Prelude hiding (FilePath)
import qualified Control.Foldl as Fold
import Data.Coerce
import Data.Maybe (mapMaybe)
import Data.Foldable(for_)
import qualified Data.Char as Char
import Data.Semigroup (Product(..))
import Control.Exception
import qualified Data.Text.IO as Text

-----------------------------------------

newtype Complexity = Complexity { unComplexity :: Text }
  deriving newtype (Show, Eq, Read, IsString)

linear :: Complexity
linear = "linear"

quadratic :: Complexity
quadratic = "quadratic"

cubic :: Complexity
cubic = "cubic"

complexities :: [Complexity]
--complexities = [linear, quadratic, cubic]
complexities = [linear]

-----------------------------------------

newtype Strategy = Strategy { unStrategy :: Text }
  deriving newtype (Show, Eq, Read, IsString)

sequential :: Strategy
sequential = "Sequential"

roundRobin :: Strategy
roundRobin = "RoundRobin"

roundRobinWeighted :: Strategy
roundRobinWeighted = "RoundRobinWeighted"

powerOfTwoChoices :: Strategy
powerOfTwoChoices = "PowerOfTwoChoices"

maximalMatchesEnumeration :: Strategy
maximalMatchesEnumeration = "MaximalMatchesEnumeration"

strategies :: [Strategy]
-- strategies = [sequential, roundRobin, roundRobinWeighted, powerOfTwoChoices, maximalMatchesEnumeration]
strategies = [sequential, roundRobin, maximalMatchesEnumeration]

-----------------------------------------

newtype Workers = Workers { unWorkers :: Text }
  deriving newtype (Show, Eq, Read, IsString)

workerss :: [Workers]
-- workerss = ["workers1", "workers4", "workers8"]
workerss = ["workers4"]

-----------------------------------------

data Benchmark = Benchmark
  { benchmarkDir    :: FilePath
  , benchmarkNumber :: Integer
  }
  deriving stock (Show, Eq)

-- | >>> getBenchDir "benchmark/"
-- [FilePath "benchmark0",FilePath "benchmark1"]
getBenchDir :: MonadIO m => ArgsOpts -> FilePath -> m [Benchmark]
getBenchDir ArgsOpts{..} root = do
  fps <- fold (ls root) Fold.list
  return $ mapMaybe (toBenchmark . basename) fps
  where
    pat :: Pattern Integer
    pat = "benchmark"
      >> (case mode of
            All -> decimal
            Only b -> b <$ satisfy (\c -> Char.digitToInt c == fromIntegral b))

    toBenchmark :: FilePath -> Maybe Benchmark
    toBenchmark (fpToText -> fp) =
      case match pat fp of
        [n]  -> Just $ Benchmark (fromText fp) n
        _ -> Nothing

-----------------------------------------

newtype Query = Query { unQuery :: Text }
  deriving newtype (Show, Eq, Read, IsString)

-- | >>> getQueries "benchmark/benchmark0"
-- ["query5","query4","query1","query2","query3"]
getQueries :: MonadIO m => FilePath -> m [Query]
getQueries benchmark = do
  fps <- fold (ls benchmark) Fold.list
  return $ coerce (mapMaybe (toQuery . basename) fps)
  where
    pat :: Pattern Integer
    pat = "query" >> decimal

    toQuery :: FilePath -> Maybe Query
    toQuery (fpToText -> fp) =
      case match pat fp of
        [] -> Nothing
        _  -> Just (coerce fp)

-----------------------------------------

data Scenario = Scenario
  { sBenchmark  :: Benchmark
  , sQuery      :: Query
  , sWorkers    :: Workers
  , sComplexity :: Complexity
  , sStrategy   :: Strategy
  }
  deriving stock (Show, Eq)

-- benchmark1.query4.workers4.linear.MaximalMatchesEnumeration
scenarioToJVMClass :: Scenario -> Text
scenarioToJVMClass Scenario{..} =
   coerce $
     format (s % "." % s % "." % s % "." % s % "." % s)
            (fpToText $ benchmarkDir sBenchmark)
            (coerce sQuery)
            (coerce sWorkers)
            (coerce sComplexity)
            (coerce sStrategy)

scenarioToFilename :: Scenario -> Text
scenarioToFilename Scenario{..} =
   coerce $
     format (s % "_" % s % "_" % s % "_" % s % "_" % s)
            (fpToText $ benchmarkDir sBenchmark)
            (coerce sQuery)
            (coerce sWorkers)
            (coerce sComplexity)
            (coerce sStrategy)

-----------------------------------------

-- Everything

-- Redirects the stderr to the stdin, and the stdin is discarded.
discardOutput :: Text
discardOutput  = "> /dev/null 2>&1"

-- Sbt is **really slow** to startup.
-- Bloop doesn't support sbt plugins AFAIK.

runSbt :: MonadIO m => Text -> m ()
runSbt subCmd =
  shells (format ("sbt \"" % s %  "\" " % s) subCmd discardOutput) empty

runScenario :: MonadIO m => Scenario -> m ()
runScenario = runSbt . toSbtCmd . scenarioToJVMClass where
  toSbtCmd = format ("benchmark/multi-jvm:run " % s)

runMake :: MonadIO m => Text -> m ()
runMake subCmd = do
  let cmd = format ("make " % s % " " % s) subCmd discardOutput
  liftIO $ Text.putStrLn cmd
  shells cmd empty

saveOutput :: MonadIO m => FilePath -> FilePath -> Scenario -> m ()
saveOutput rootDir outputDir scenario = do
  createDirIfNotExists outputDir
  printf ("Saving output to " % fp % "/ ... \n") outputDir

  let targetDir = rootDir </> "target"

      statsInFilePath = targetDir </> "stats.log"
      timeInFilePath = targetDir </> "time.log"

      statsOutFilePath = outputDir </> fromText (scenarioToFilename scenario <> "_stats.log")
      timeOutFilePath = outputDir </> fromText (scenarioToFilename scenario <> "_time.log")

  mv statsInFilePath statsOutFilePath
  mv timeInFilePath timeOutFilePath

-----------------------------------------
-- Command-line parsing

data Mode
  = All -- ^ Run all benchmarks
  | Only { benchmark :: Integer } -- ^ Run onlu the given benchmark
  deriving stock (Show)

data ArgsOpts = ArgsOpts
  { mode      :: Mode
  , isClean   :: Bool
  , outputDir :: FilePath
  }
  deriving stock (Show)

modeParser :: Parser Mode
modeParser = subcommandAll <|> subcommandOnly where
  subcommandAll :: Parser Mode
  subcommandAll = Turtle.subcommand "all" "Run all benchmarks" (pure All)

  subcommandOnly :: Parser Mode
  subcommandOnly = Turtle.subcommand "only" "Run only the given benchmark" (Only <$> optInteger "benchmark" 'b' "Benchmark number")

cleanParser :: Parser Bool
cleanParser = switch "clean" 'c' "Clean run"

outputParser :: Parser FilePath
outputParser = optPath "output" 'o' "Output folder"

-- | Command-line parsing with description and help.
parseArgs :: MonadIO m => m ArgsOpts
parseArgs = Turtle.options "Benchmarks Script" parser where
  parser = ArgsOpts <$> modeParser <*> cleanParser <*> (outputParser <|> pure "./output")

-------------------------------------------
-- Utils

fpToText :: FilePath -> Text
fpToText fp =
  case toText fp of
    Left approx -> error "fpToText"
    Right fp -> fp

data Any = forall a. Any a

toAny :: [a] -> [Any]
toAny = fmap Any

countTotal :: [[Any]] -> Int
countTotal = getProduct . foldMap (Product . length)

fori_ :: Applicative f => [a] -> ((a, Int) -> f b) -> f ()
fori_ xs = for_ (zip xs [0..])

createDirIfNotExists :: MonadIO m => FilePath -> m ()
createDirIfNotExists fp = do
  dirExists <- testdir fp
  unless dirExists (mkdir fp)

-------------------------------------------

main :: IO ()
main = do
  args <- parseArgs
  -- TODO root can be retrieved from `git rev-parse --show-toplevel`
  r <- try $ runBenchmarks "benchmark/" args
  case r of
    Right elapsedTime -> do
      echo "Status: ✅"
      echo $ "Benchmarks have finished in " <> unsafeTextToLine (repr elapsedTime)
    Left (e :: SomeException) -> do
      echo "Status: 🚫"
      echo $ "Benchmarks failed: " <> unsafeTextToLine (repr e)

runBenchmarks :: FilePath -> ArgsOpts -> IO NominalDiffTime
runBenchmarks rootDir args = do
  when (isClean args) $
    runMake "benchmarks"
  benchmarks <- getBenchDir args rootDir
  (_, elapsedTime) <- time $
    fori_ benchmarks $ \(benchmark, i) -> do
      queries <- getQueries (rootDir </> benchmarkDir benchmark)
      let total = countTotal [toAny benchmarks, toAny queries, toAny workerss, toAny complexities, toAny strategies]
      fori_ queries $ \(query, j) ->
        fori_ workerss $ \(workers, k) -> do
        fori_ complexities $ \(complexity, l) -> do
          fori_ strategies $ \(strategy, m) -> do
            let
              scenario = Scenario benchmark query workers complexity strategy
              current =
                  i * (length queries * length workerss * length complexities * length strategies)
                    + j * (length workerss * length complexities * length strategies)
                    + k * (length complexities * length strategies)
                    + l * length strategies
                    + m
                    + 1
            printf (s % " (" % d % "/" % d % ")\n") (scenarioToFilename scenario) current total
            runScenario scenario
            saveOutput rootDir (outputDir args) scenario
  return elapsedTime
