#!/usr/bin/env stack
-- stack --resolver lts-18.13 script --package turtle --package foldl --package bytestring --package text --package containers --package cassava --package vector --package attoparsec --package unordered-containers
{-# LANGUAGE DerivingStrategies #-}
{-# LANGUAGE GADTs #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE NamedFieldPuns #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE RecordWildCards #-}
{-# LANGUAGE ScopedTypeVariables #-}
{-# LANGUAGE ViewPatterns #-}
{-# LANGUAGE LambdaCase #-}

-----------------------------------------

import Control.Exception
import qualified Control.Foldl as Fold
import qualified Data.Char as Char
import Data.Coerce
import Data.Foldable (for_, traverse_)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as Map
import Data.Maybe (mapMaybe)
import Data.Semigroup (Product (..))
import qualified Data.Text as Text
import qualified Data.Text.IO as Text
import qualified Data.Text.Encoding as Text
import Turtle
import Prelude hiding (FilePath)
import Data.Csv (Header, NamedRecord)
import qualified Data.Csv as Csv
import qualified Data.Csv.Parser as Csv
import qualified Data.Vector as Vector
import qualified Data.Attoparsec.ByteString as Atto
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Control.Foldl (Vector)
import GHC.Base (assert)
import GHC.IO (mkUserError)
import Control.Exception (throwIO)
import qualified Data.HashMap.Strict as HashMap

-----------------------------------------

data Project = Core | Core2
  deriving (Show, Eq, Ord)

project2Text :: Project -> Text
project2Text = Text.toLower . Text.pack . show

instance Read Project where
  readsPrec _ "core" = [(Core, [])]
  readsPrec _ "core2" = [(Core2, [])]
  readsPrec _ str = fail ("Cannot parse: " ++ str)

-----------------------------------------

newtype Complexity = Complexity {unComplexity :: Text}
  deriving newtype (Show, Eq, Read, IsString)

none :: Complexity
none = "none"

linear :: Complexity
linear = "linear"

quadratic :: Complexity
quadratic = "quadratic"

cubic :: Complexity
cubic = "cubic"

complexities :: [Complexity]
--complexities = [none, linear, quadratic, cubic]
complexities = [none]

-----------------------------------------

newtype Strategy = Strategy {unStrategy :: Text}
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

maximalMatchesDisjointEnumeration :: Strategy
maximalMatchesDisjointEnumeration = "MaximalMatchesDisjointEnumeration"

distributed :: Strategy
distributed = "Distributed"

strategiesDict :: Map Project [Strategy]
strategiesDict =
  Map.fromList
    -- [ (Core, [sequential, roundRobin, roundRobinWeighted, powerOfTwoChoices, maximalMatchesEnumeration, maximalMatchesDisjointEnumeration]),
    [ (Core, [roundRobin]),
      -- (Core2, [sequential, distributed])
      (Core2, [distributed])
    ]

-----------------------------------------

newtype Workers = Workers {unWorkers :: Text}
  deriving newtype (Show, Eq, Read, IsString)

workerss :: [Workers]
-- workerss = (Workers . ("workers" <>)) . Text.pack . show <$> [1,2,4,6,8]
workerss = (Workers . ("workers" <>)) . Text.pack . show <$> [10]

-----------------------------------------

data Benchmark = Benchmark
  { benchmarkDir :: FilePath,
    benchmarkNumber :: Integer
  }
  deriving stock (Show, Eq)

-- | >>> getBenchDir "benchmark/"
-- [FilePath "benchmark0",FilePath "benchmark1"]
getBenchDir :: MonadIO m => ArgsOpts -> FilePath -> m [Benchmark]
getBenchDir ArgsOpts {..} root = do
  fps <- fold (ls root) Fold.list
  return $ mapMaybe (toBenchmark . basename) fps
  where
    pat :: Pattern Integer
    pat =
      "benchmark"
        >> ( case mode of
               All -> decimal
               Only b -> b <$ satisfy (\c -> Char.digitToInt c == fromIntegral b)
           )

    toBenchmark :: FilePath -> Maybe Benchmark
    toBenchmark (fpToText -> fp) =
      case match pat fp of
        [n] -> Just $ Benchmark (fromText fp) n
        _ -> Nothing

-----------------------------------------

newtype Query = Query {unQuery :: Text}
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
        _ -> Just (coerce fp)

-----------------------------------------

data Scenario = Scenario
  { sProject :: Project,
    sBenchmark :: Benchmark,
    sQuery :: Query,
    sWorkers :: Workers,
    sComplexity :: Complexity,
    sStrategy :: Strategy,
    sIteration :: Int
  }
  deriving stock (Show, Eq)

scenarioToJVMClass :: Scenario -> Text
scenarioToJVMClass Scenario {..} =
  coerce $
    format
      (s % "." % s % "." % s % "." % s % "." % s % "." % s)
      (project2Text sProject)
      (fpToText $ benchmarkDir sBenchmark)
      (coerce sQuery)
      (coerce sWorkers)
      (coerce sComplexity)
      (coerce sStrategy)

scenarioToFilename :: Scenario -> Text
scenarioToFilename Scenario {..} =
  coerce $
    format
      (s % "_" % s % "_" % s % "_" % s % "_" % s % "_" % s % "_" % d)
      (project2Text sProject)
      (fpToText $ benchmarkDir sBenchmark)
      (coerce sQuery)
      (coerce sWorkers)
      (coerce sComplexity)
      (coerce sStrategy)
      sIteration

-----------------------------------------

-- Everything

-- Redirects the stderr to the stdin, and the stdin is discarded.
discardOutput :: Text
discardOutput = "> /dev/null 2>&1"

-- Sbt is **really slow** to startup.
-- Bloop doesn't support sbt plugins AFAIK.

runSbt :: MonadIO m => Text -> m ()
runSbt subCmd =
  shells (format ("sbt \"" % s % "\" " % s) subCmd discardOutput) empty

runScenario :: MonadIO m => Scenario -> m ()
runScenario = runSbt . toSbtCmd . scenarioToJVMClass
  where
    toSbtCmd = format ("benchmark/multi-jvm:run " % s)

runMake :: MonadIO m => Text -> m ()
runMake subCmd = do
  let cmd = format ("make " % s % " " % s) subCmd discardOutput
  liftIO $ Text.putStrLn cmd
  shells cmd empty

saveOutput :: MonadIO m => FilePath -> FilePath -> Scenario -> m ()
saveOutput logsDir outputDir scenario = do
  createDirIfNotExists outputDir
  printf ("Saving output to " % fp % "/ ... \n") outputDir
  parseStatsLog logsDir >>= writeCsv (outputDir </> "stats.csv") scenario
  parseTimeLog logsDir >>= writeCsv (outputDir </> "time.csv") scenario
  rm (logsDir </> "matches.log")
  rm (logsDir </> "stats.log")
  rm (logsDir </> "time.log")

parseStatsLog :: MonadIO m => FilePath -> m (Header, Vector.Vector NamedRecord)
parseStatsLog logsDir = parseCsvFile (logsDir </> "stats.log")

parseTimeLog :: MonadIO m => FilePath -> m (Header, Vector.Vector NamedRecord)
parseTimeLog logsDir = parseCsvFile (logsDir </> "time.log")

parseCsvFile :: MonadIO m => FilePath -> m (Header, Vector.Vector NamedRecord)
parseCsvFile = liftIO . parseCsvFile'

parseCsvFile' :: FilePath -> IO (Header, Vector.Vector NamedRecord)
parseCsvFile' fp = do
  bs <- BS.readFile (encodeString fp)
  case Atto.parseOnly (Csv.csvWithHeader Csv.defaultDecodeOptions) bs of
    Left err -> throwIO (mkUserError err)
    Right a -> return a

writeCsv :: MonadIO m => FilePath -> Scenario -> (Header, Vector.Vector NamedRecord) -> m ()
writeCsv fp scenario (newHeader, newRecords) = do
  previousRecords <- liftIO previousContent
  let header = Vector.cons "scenario" newHeader
      scenarioBS = Text.encodeUtf8 (scenarioToFilename scenario)
      newRecords' = HashMap.insert "scenario" scenarioBS <$> newRecords
      records = previousRecords <> newRecords'
      lbs = Csv.encodeByName header (Vector.toList records)
  liftIO $ LBS.writeFile (encodeString fp) lbs
  where
    previousContent :: IO (Vector.Vector NamedRecord)
    previousContent = do
      r <- try (parseCsvFile' fp)
      case r of
        Left (_ :: IOException) -> return Vector.empty
        Right (_, records) -> return records

-----------------------------------------
-- Command-line parsing

data Mode
  = -- | Run all benchmarks
    All
  | -- | Run only the given benchmark
    Only {benchmark :: Integer}
  deriving stock (Show)

data ArgsOpts = ArgsOpts
  { mode :: Mode,
    project :: Project,
    isClean :: Bool,
    outputDir :: FilePath,
    iterations :: Int
  }
  deriving stock (Show)

modeParser :: Turtle.Parser Mode
modeParser = subcommandAll <|> subcommandOnly
  where
    subcommandAll :: Parser Mode
    subcommandAll = Turtle.subcommand "all" "Run all benchmarks" (pure All)

    subcommandOnly :: Parser Mode
    subcommandOnly = Turtle.subcommand "only" "Run only the given benchmark" (Only <$> optInteger "benchmark" 'b' "Benchmark number")

projectParser :: Turtle.Parser Project
projectParser = optRead "project" 'p' "Available: core and core2"

cleanParser :: Turtle.Parser Bool
cleanParser = switch "clean" 'c' "Clean run" <|> pure False

outputParser :: Turtle.Parser FilePath
outputParser = optPath "output" 'o' "Output folder" <|> pure "./output"

iterationsParser :: Turtle.Parser Int
iterationsParser = optInt "iterations" 'i' "Iterations per scenario" <|> pure 1

-- | Command-line parsing with description and help.
parseArgs :: MonadIO m => m ArgsOpts
parseArgs = Turtle.options "Benchmarks Script" parser
  where
    parser =
      ArgsOpts
        <$> modeParser
        <*> projectParser
        <*> cleanParser
        <*> outputParser
        <*> iterationsParser

-------------------------------------------
-- Utils

fpToText :: FilePath -> Text
fpToText fp =
  case toText fp of
    Left approx -> error "fpToText"
    Right fp -> fp

-- GHC 9.2 allows [forall a. a]
-- https://downloads.haskell.org/~ghc/9.2.1/docs/html/users_guide/exts/impredicative_types.html#extension-ImpredicativeTypes
data Any = forall a. Any a

toAny :: [a] -> [Any]
toAny = fmap Any

countTotal :: [[Any]] -> Int
countTotal = getProduct . foldMap (Product . length)

fori_ :: Applicative f => [a] -> ((a, Int) -> f b) -> f ()
fori_ xs = for_ (zip xs [0 ..])

deleteDirIfExists :: MonadIO m => FilePath -> m ()
deleteDirIfExists dir = do
  b <- testdir dir
  when b $ rmtree dir

createDirIfNotExists :: MonadIO m => FilePath -> m ()
createDirIfNotExists fp = do
  dirExists <- testdir fp
  unless dirExists (mkdir fp)

-------------------------------------------

-- TODO root can be retrieved from `git rev-parse --show-toplevel`

main :: IO ()
main = do
  args <- parseArgs
  r <- try $ runBenchmarks "benchmark/" args
  case r of
    Right elapsedTime -> do
      echo "Status: OK"
      echo $ "Benchmarks have finished in " <> unsafeTextToLine (repr elapsedTime)
    Left (e :: SomeException) -> do
      echo "Status: KO"
      echo $ "Benchmarks failed: " <> unsafeTextToLine (repr e)

runBenchmarks :: FilePath -> ArgsOpts -> IO NominalDiffTime
runBenchmarks rootDir args@ArgsOpts {isClean, project, outputDir, iterations, ..} = do
  when isClean $
    runMake "benchmarks"
  deleteDirIfExists outputDir
  benchmarks <- getBenchDir args rootDir
  let strategies = strategiesDict Map.! project
  (_, elapsedTime) <- time $
    fori_ benchmarks $ \(benchmark, i) -> do
      queries <- getQueries (rootDir </> benchmarkDir benchmark </> fromText (project2Text project))
      let total = countTotal [toAny benchmarks, toAny queries, toAny workerss, toAny complexities, toAny strategies, toAny [1..iterations]]
      fori_ queries $ \(query, j) ->
        fori_ workerss $ \(workers, k) -> do
          fori_ complexities $ \(complexity, l) -> do
            fori_ strategies $ \(strategy, m) -> do
              fori_ [1..iterations] $ \(iteration, n) -> do
                let scenario = Scenario project benchmark query workers complexity strategy iteration
                    current =
                      i * (length queries * length workerss * length complexities * length strategies * iterations)
                        + j * (length workerss * length complexities * length strategies * iterations)
                        + k * (length complexities * length strategies * iterations)
                        + l * (length strategies * iterations)
                        + m * iterations
                        + n
                        + 1
                printf (s % " (" % d % "/" % d % ")\n") (scenarioToFilename scenario) current total
                runScenario scenario
                saveOutput (rootDir </> "target") outputDir scenario
  return elapsedTime
